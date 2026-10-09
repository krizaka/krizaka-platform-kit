package com.krizaka.messaging.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import com.krizaka.messaging.outbox.NewOutboxMessage;
import com.krizaka.messaging.topology.MessagingExchanges;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import tools.jackson.databind.json.JsonMapper;

class OutboxEventPublisherTest {

  private static final Instant NOW = Instant.parse("2026-10-09T08:15:30.123Z");

  private final RecordingOutboxStore store = new RecordingOutboxStore();
  private final OutboxEventPublisher events =
      new OutboxEventPublisher(
          store,
          JsonMapper.shared(),
          new MessagingExchanges("platform.events", "platform.dlx"),
          "krizaka-users",
          Clock.fixed(NOW, ZoneOffset.UTC));

  record UserRegistered(String userId, String email) {}

  @AfterEach
  void clearMdc() {
    MDC.clear();
  }

  @Test
  void writesTheBareEventToTheEventsExchangeWithTheFiveHeaders() {
    MDC.put("requestId", "req-42");

    events.publish("evt.user.registered", 1, new UserRegistered("u-1", "a@b.c"));

    NewOutboxMessage row = store.appended.getFirst();
    assertThat(row.exchange()).isEqualTo("platform.events");
    assertThat(row.routingKey()).isEqualTo("evt.user.registered");
    assertThat(new String(row.body(), StandardCharsets.UTF_8))
        .isEqualTo("{\"userId\":\"u-1\",\"email\":\"a@b.c\"}");
    assertThat(row.headers())
        .hasSize(5)
        .containsEntry(EventHeaders.TYPE, "evt.user.registered")
        .containsEntry(EventHeaders.VERSION, "1")
        .containsEntry(EventHeaders.PRODUCER, "krizaka-users")
        .containsEntry(EventHeaders.CORRELATION, "req-42")
        .containsEntry(EventHeaders.OCCURRED_AT, "2026-10-09T08:15:30.123Z");
  }

  @Test
  void theMessageIdIsChosenAtWriteTimeAndUniquePerEvent() {
    events.publish("evt.user.registered", 1, new UserRegistered("u-1", "a@b.c"));
    events.publish("evt.user.registered", 1, new UserRegistered("u-1", "a@b.c"));

    String first = store.appended.get(0).messageId();
    String second = store.appended.get(1).messageId();
    assertThat(UUID.fromString(first)).isNotNull();
    assertThat(first).isNotEqualTo(second);
  }

  @Test
  void anEventOutsideARequestGetsItsOwnCorrelationId() {
    events.publish("evt.user.registered", 1, new UserRegistered("u-1", "a@b.c"));

    assertThat(UUID.fromString(store.appended.getFirst().headers().get(EventHeaders.CORRELATION)))
        .isNotNull();
    assertThat(MDC.get("requestId")).isNull();
  }

  @Test
  void refusesAnEventThatCannotBeRouted() {
    assertThatIllegalArgumentException().isThrownBy(() -> events.publish(" ", 1, "x"));
    assertThatIllegalArgumentException()
        .isThrownBy(() -> events.publish("evt.user.registered", 0, "x"))
        .withMessageContaining("version");
    assertThat(store.appended).isEmpty();
  }

  @Test
  void aPublisherWithoutAProducerCannotBeBuilt() {
    assertThatIllegalArgumentException()
        .isThrownBy(
            () ->
                new OutboxEventPublisher(
                    store,
                    JsonMapper.shared(),
                    MessagingExchanges.defaults(),
                    "",
                    Clock.systemUTC()));
  }
}
