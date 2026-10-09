package com.krizaka.messaging.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.AmqpTemplate;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

class OutboxRelayTest {

  private static final Instant NOW = Instant.parse("2026-10-08T12:00:00Z");

  private final InMemoryOutboxStore store = new InMemoryOutboxStore();
  private final AmqpTemplate amqp = mock(AmqpTemplate.class);
  private final OutboxRelay relay =
      new OutboxRelay(
          store,
          amqp,
          new TransactionTemplate(mock(PlatformTransactionManager.class)),
          10,
          Duration.ofDays(7),
          Clock.fixed(NOW, ZoneOffset.UTC));

  @Test
  void publishesAPendingRowAsAPersistentJsonMessageAndMarksIt() {
    OutboxMessage row = row("m-1", "{\"credits\":5}");
    store.pending.add(row);

    assertThat(relay.relayPendingBatch()).isEqualTo(1);

    ArgumentCaptor<Message> sent = ArgumentCaptor.forClass(Message.class);
    verify(amqp).send(eq("platform.events"), eq("evt.wallet.debited"), sent.capture());
    MessageProperties properties = sent.getValue().getMessageProperties();
    assertThat(properties.getMessageId()).isEqualTo("m-1");
    assertThat(properties.getContentType()).isEqualTo(MessageProperties.CONTENT_TYPE_JSON);
    assertThat(properties.getDeliveryMode()).isEqualTo(MessageDeliveryMode.PERSISTENT);
    assertThat(new String(sent.getValue().getBody(), StandardCharsets.UTF_8))
        .isEqualTo("{\"credits\":5}");
    assertThat(store.published).containsExactly(row.id());
  }

  @Test
  void aFailedPublishIsRecordedAndDoesNotStopTheBatch() {
    OutboxMessage broken = row("m-broken", "{}");
    OutboxMessage fine = row("m-fine", "{}");
    store.pending.add(broken);
    store.pending.add(fine);
    doThrow(new AmqpException("broker down"))
        .when(amqp)
        .send(
            any(String.class),
            any(String.class),
            org.mockito.ArgumentMatchers.argThat(
                message -> "m-broken".equals(message.getMessageProperties().getMessageId())));

    assertThat(relay.relayPendingBatch()).isEqualTo(1);

    assertThat(store.failed).containsExactly(broken.id());
    assertThat(store.published).containsExactly(fine.id());
  }

  @Test
  void aRowWithoutAMessageIdIsPublishedWithoutOne() {
    store.pending.add(row(null, "{}"));

    relay.relayPendingBatch();

    ArgumentCaptor<Message> sent = ArgumentCaptor.forClass(Message.class);
    verify(amqp).send(any(String.class), any(String.class), sent.capture());
    assertThat(sent.getValue().getMessageProperties().getMessageId()).isNull();
  }

  @Test
  void purgesRowsPublishedBeforeTheRetentionWindow() {
    assertThat(relay.purgePublished()).isEqualTo(3);
    assertThat(store.purgeCutoff).isEqualTo(NOW.minus(Duration.ofDays(7)));
  }

  private static OutboxMessage row(String messageId, String json) {
    return new OutboxMessage(
        UUID.randomUUID(),
        "platform.events",
        "evt.wallet.debited",
        messageId,
        json.getBytes(StandardCharsets.UTF_8),
        0);
  }
}
