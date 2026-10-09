package com.krizaka.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.krizaka.messaging.dedup.MessageDedup;
import com.krizaka.messaging.event.EventHeaders;
import com.krizaka.messaging.event.EventPublisher;
import com.krizaka.messaging.outbox.NewOutboxMessage;
import com.krizaka.messaging.outbox.OutboxMessage;
import com.krizaka.messaging.outbox.OutboxStore;
import com.krizaka.messaging.topology.KrizakaQueues;
import com.krizaka.messaging.topology.MessagingExchanges;
import com.krizaka.test.container.AbstractContainerIntegrationTest;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Headers;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

/**
 * The whole path against a real PostgreSQL and RabbitMQ: an event published in a transaction is
 * written to a JDBC outbox (the recommended schema), relayed with its envelope, received by a
 * listener as a typed object; a failing listener is retried with back-off, then dead-lettered with
 * its envelope; a replayed message is refused by the JDBC deduplication.
 */
@SpringBootTest(
    classes = MessagingRoundTripIT.App.class,
    properties = {
      "krizaka.messaging.producer=kit-it",
      "krizaka.messaging.exchanges.events=kit-it.events",
      "krizaka.messaging.exchanges.dead-letter=kit-it.dlx",
      "krizaka.messaging.dedup.store=jdbc",
      "krizaka.messaging.outbox.poll-interval=100ms",
      "krizaka.messaging.retry.max-attempts=3",
      "krizaka.messaging.retry.initial=200ms",
      "krizaka.messaging.retry.multiplier=2",
      "krizaka.messaging.retry.max=1s"
    })
class MessagingRoundTripIT extends AbstractContainerIntegrationTest {

  static final String RECEIVED = "kit-it.received";
  static final String FLAKY = "kit-it.flaky";
  static final String POISON = "kit-it.poison";

  @Autowired EventPublisher events;
  @Autowired TransactionTemplate transactions;
  @Autowired RabbitTemplate rabbit;
  @Autowired Listeners listeners;
  @Autowired JdbcTemplate jdbc;

  /** The event contract, as the consumer's own tolerant copy. */
  record Registered(String userId) {}

  @Test
  void aPublishedEventReachesTheListenerWithItsEnvelope() throws Exception {
    MDC.put("requestId", "req-it-1");
    try {
      transactions.executeWithoutResult(
          status -> events.publish("evt.it.received", 1, new Registered("u-1")));
    } finally {
      MDC.remove("requestId");
    }

    Received received = listeners.received.poll(20, TimeUnit.SECONDS);
    assertThat(received).isNotNull();
    assertThat(received.event()).isEqualTo(new Registered("u-1"));
    assertThat(received.headers())
        .containsEntry(EventHeaders.TYPE, "evt.it.received")
        .containsEntry(EventHeaders.VERSION, "1")
        .containsEntry(EventHeaders.PRODUCER, "kit-it")
        .containsEntry(EventHeaders.CORRELATION, "req-it-1")
        .containsKey(EventHeaders.OCCURRED_AT);
    String storedId =
        jdbc.queryForObject(
            "SELECT message_id FROM kit_it_outbox WHERE payload->>'userId' = 'u-1'", String.class);
    assertThat(received.messageId()).isEqualTo(storedId);
  }

  @Test
  void anEventIsNotPublishedWhenItsTransactionRollsBack() throws Exception {
    transactions.executeWithoutResult(
        status -> {
          events.publish("evt.it.received", 1, new Registered("rolled-back"));
          status.setRollbackOnly();
        });

    assertThat(listeners.received.poll(1, TimeUnit.SECONDS)).isNull();
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM kit_it_outbox WHERE payload->>'userId' = 'rolled-back'",
                Integer.class))
        .isZero();
  }

  @Test
  void aListenerThatFailsTwiceIsRetriedWithBackOffAndSucceeds() {
    transactions.executeWithoutResult(
        status -> events.publish("evt.it.flaky", 1, new Registered("u-flaky")));

    await().atMost(Duration.ofSeconds(20)).until(() -> listeners.flakySucceeded.get() == 1);
    assertThat(listeners.flakyAttempts).hasSize(3);
    Duration firstWait =
        Duration.between(listeners.flakyAttempts.get(0), listeners.flakyAttempts.get(1));
    Duration secondWait =
        Duration.between(listeners.flakyAttempts.get(1), listeners.flakyAttempts.get(2));
    assertThat(firstWait).isGreaterThanOrEqualTo(Duration.ofMillis(180));
    assertThat(secondWait).isGreaterThanOrEqualTo(Duration.ofMillis(360));
  }

  @Test
  void aListenerThatAlwaysFailsEndsInTheDlqWithTheOriginalHeaders() {
    transactions.executeWithoutResult(
        status -> events.publish("evt.it.poison", 1, new Registered("u-poison")));

    Message dead = rabbit.receive(KrizakaQueues.deadLetterQueue(POISON), 20_000);

    assertThat(dead).isNotNull();
    assertThat(listeners.poisonAttempts.get()).isEqualTo(3);
    assertThat(dead.getMessageProperties().getHeaders())
        .containsEntry(EventHeaders.TYPE, "evt.it.poison")
        .containsEntry(EventHeaders.PRODUCER, "kit-it")
        .containsKey(EventHeaders.CORRELATION)
        .containsEntry("x-exception-message", "poison");
    // jsonb normalises whitespace: the same JSON value, not the same bytes.
    assertThat(JsonMapper.shared().readTree(dead.getBody()))
        .isEqualTo(JsonMapper.shared().readTree("{\"userId\":\"u-poison\"}"));
  }

  @Test
  void aReplayedMessageIdIsRefusedByTheDeduplication() throws Exception {
    transactions.executeWithoutResult(
        status -> events.publish("evt.it.received", 1, new Registered("u-replayed")));
    Received first = listeners.received.poll(20, TimeUnit.SECONDS);
    assertThat(first).isNotNull();
    int duplicatesBefore = listeners.duplicates.get();

    // What a relay that crashed after publishing does: the same row, the same id, sent again.
    MessageProperties properties = new MessageProperties();
    properties.setMessageId(first.messageId());
    properties.setContentType(MessageProperties.CONTENT_TYPE_JSON);
    properties.setHeader(EventHeaders.TYPE, "evt.it.received");
    rabbit.send(
        "kit-it.events",
        "evt.it.received",
        new Message("{\"userId\":\"u-replayed\"}".getBytes(StandardCharsets.UTF_8), properties));

    await()
        .atMost(Duration.ofSeconds(20))
        .until(() -> listeners.duplicates.get() == duplicatesBefore + 1);
    assertThat(listeners.received.poll(500, TimeUnit.MILLISECONDS)).isNull();
  }

  /** What the receiving listener saw. */
  record Received(Registered event, String messageId, Map<String, Object> headers) {}

  /** The listeners under test. */
  static class Listeners {

    final BlockingQueue<Received> received = new LinkedBlockingQueue<>();
    final AtomicInteger duplicates = new AtomicInteger();
    final List<Instant> flakyAttempts = new CopyOnWriteArrayList<>();
    final AtomicInteger flakySucceeded = new AtomicInteger();
    final AtomicInteger poisonAttempts = new AtomicInteger();
    private final MessageDedup dedup;

    Listeners(MessageDedup dedup) {
      this.dedup = dedup;
    }

    @RabbitListener(queues = RECEIVED)
    void received(
        @Payload Registered event,
        @Header("amqp_messageId") String messageId,
        @Headers Map<String, Object> headers) {
      if (!dedup.claim(RECEIVED, messageId)) {
        duplicates.incrementAndGet();
        return;
      }
      received.add(new Received(event, messageId, headers));
    }

    @RabbitListener(queues = FLAKY)
    void flaky(Registered event) {
      flakyAttempts.add(Instant.now());
      if (flakyAttempts.size() < 3) {
        throw new IllegalStateException("not yet");
      }
      flakySucceeded.incrementAndGet();
    }

    @RabbitListener(queues = POISON)
    void poison(Registered event) {
      poisonAttempts.incrementAndGet();
      throw new IllegalStateException("poison");
    }
  }

  /** An application with a JDBC outbox over the recommended schema, and three consumer queues. */
  @SpringBootConfiguration
  @EnableAutoConfiguration
  static class App {

    @Bean
    OutboxStore outboxStore(JdbcTemplate jdbc, JsonMapper json) {
      jdbc.execute(
          """
          CREATE TABLE IF NOT EXISTS kit_it_outbox (
              id              UUID         PRIMARY KEY,
              exchange        VARCHAR(100) NOT NULL,
              routing_key     VARCHAR(255) NOT NULL,
              message_id      VARCHAR(255) NOT NULL UNIQUE,
              payload         JSONB        NOT NULL,
              headers         JSONB        NOT NULL DEFAULT '{}'::jsonb,
              created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
              published_at    TIMESTAMPTZ,
              attempts        INT          NOT NULL DEFAULT 0,
              next_attempt_at TIMESTAMPTZ  NOT NULL DEFAULT now()
          )""");
      jdbc.execute(
          """
          CREATE TABLE IF NOT EXISTS processed_messages (
              consumer     VARCHAR(255) NOT NULL,
              message_id   VARCHAR(255) NOT NULL,
              processed_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
              PRIMARY KEY (consumer, message_id)
          )""");
      return new JdbcOutboxStore(jdbc, json);
    }

    @Bean
    Declarables received(MessagingExchanges exchanges) {
      return KrizakaQueues.consumer(exchanges, RECEIVED, "evt.it.received");
    }

    @Bean
    Declarables flaky(MessagingExchanges exchanges) {
      return KrizakaQueues.consumer(exchanges, FLAKY, "evt.it.flaky");
    }

    @Bean
    Declarables poison(MessagingExchanges exchanges) {
      return KrizakaQueues.consumer(exchanges, POISON, "evt.it.poison");
    }

    @Bean
    Listeners listeners(MessageDedup dedup) {
      return new Listeners(dedup);
    }
  }

  /** The recommended JDBC outbox store, as a context would write it. */
  static final class JdbcOutboxStore implements OutboxStore {

    private final JdbcTemplate jdbc;
    private final JsonMapper json;

    JdbcOutboxStore(JdbcTemplate jdbc, JsonMapper json) {
      this.jdbc = jdbc;
      this.json = json;
    }

    @Override
    public void append(NewOutboxMessage message) {
      jdbc.update(
          "INSERT INTO kit_it_outbox (id, exchange, routing_key, message_id, payload, headers)"
              + " VALUES (?, ?, ?, ?, ?::jsonb, ?::jsonb)",
          UUID.randomUUID(),
          message.exchange(),
          message.routingKey(),
          message.messageId(),
          new String(message.body(), StandardCharsets.UTF_8),
          json.writeValueAsString(message.headers()));
    }

    @Override
    public List<OutboxMessage> lockPendingBatch(int batchSize) {
      return jdbc.query(
          "SELECT id, exchange, routing_key, message_id, payload::text, headers::text, attempts"
              + " FROM kit_it_outbox WHERE published_at IS NULL AND next_attempt_at <= now()"
              + " ORDER BY created_at LIMIT ? FOR UPDATE SKIP LOCKED",
          (rs, row) ->
              new OutboxMessage(
                  rs.getObject("id", UUID.class),
                  rs.getString("exchange"),
                  rs.getString("routing_key"),
                  rs.getString("message_id"),
                  rs.getString("payload").getBytes(StandardCharsets.UTF_8),
                  rs.getInt("attempts"),
                  headers(rs.getString("headers"))),
          batchSize);
    }

    @Override
    public void markPublished(UUID id) {
      jdbc.update("UPDATE kit_it_outbox SET published_at = now() WHERE id = ?", id);
    }

    @Override
    public void recordFailure(UUID id, int previousAttempts) {
      jdbc.update(
          "UPDATE kit_it_outbox SET attempts = attempts + 1,"
              + " next_attempt_at = now() + interval '1 second' WHERE id = ?",
          id);
    }

    @Override
    public long purgePublishedBefore(Instant cutoff) {
      return jdbc.update(
          "DELETE FROM kit_it_outbox WHERE published_at < ?", Timestamp.from(cutoff));
    }

    @SuppressWarnings("unchecked")
    private Map<String, String> headers(String text) {
      return json.readValue(text, Map.class);
    }
  }
}
