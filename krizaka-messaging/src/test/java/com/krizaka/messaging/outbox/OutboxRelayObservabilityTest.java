package com.krizaka.messaging.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

import com.krizaka.messaging.RecordedObservations;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.AmqpTemplate;
import org.springframework.amqp.core.Message;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.Status;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

class OutboxRelayObservabilityTest {

  private static final Instant NOW = Instant.parse("2026-10-20T12:00:00Z");

  private final InMemoryOutboxStore store = new InMemoryOutboxStore();
  private final AmqpTemplate amqp = mock(AmqpTemplate.class);
  private final RecordedObservations observations = new RecordedObservations();
  private final OutboxRelay relay =
      new OutboxRelay(
          store,
          amqp,
          new TransactionTemplate(mock(PlatformTransactionManager.class)),
          10,
          Duration.ofDays(7),
          Clock.fixed(NOW, ZoneOffset.UTC),
          Duration.ofMinutes(2),
          observations.registry());

  @Test
  void aBatchAndEachPublishAreObserved() {
    store.pending.add(row());
    store.pending.add(row());

    relay.relayPendingBatch();

    List<RecordedObservations.Stopped> batches = observations.named(OutboxRelay.RELAY_OBSERVATION);
    assertThat(batches).hasSize(1);
    assertThat(batches.getFirst().low())
        .containsEntry("krizaka.outbox.store", "InMemoryOutboxStore");
    assertThat(batches.getFirst().high()).containsEntry("krizaka.outbox.published", "2");
    List<RecordedObservations.Stopped> publishes =
        observations.named(OutboxRelay.PUBLISH_OBSERVATION);
    assertThat(publishes).hasSize(2);
    assertThat(publishes.getFirst().low())
        .containsEntry("messaging.destination.name", "platform.events")
        .containsEntry("messaging.rabbitmq.destination.routing_key", "evt.wallet.debited");
    assertThat(publishes.getFirst().error()).isNull();
  }

  @Test
  void aFailedPublishIsAnObservedErrorAndCountsAsAFailure() {
    doThrow(new AmqpException("broker down"))
        .when(amqp)
        .send(anyString(), anyString(), any(Message.class));
    store.pending.add(row());

    relay.relayPendingBatch();

    assertThat(observations.named(OutboxRelay.PUBLISH_OBSERVATION).getFirst().error())
        .isInstanceOf(AmqpException.class);
    OutboxRelay.Status status = relay.status();
    assertThat(status.consecutiveFailures()).isEqualTo(1);
    assertThat(status.lastError()).isEqualTo(AmqpException.class.getName());
    assertThat(status.lastSuccess()).isNull();
  }

  @Test
  void aSuccessResetsTheFailures() {
    doThrow(new AmqpException("broker down"))
        .when(amqp)
        .send(anyString(), anyString(), any(Message.class));
    store.pending.add(row());
    relay.relayPendingBatch();
    relay.relayPendingBatch();
    assertThat(relay.status().consecutiveFailures()).isEqualTo(2);

    org.mockito.Mockito.reset(amqp);
    relay.relayPendingBatch();

    assertThat(relay.status().consecutiveFailures()).isZero();
    assertThat(relay.status().lastSuccess()).isEqualTo(NOW);
  }

  @Test
  void aBatchThatThrowsIsAnObservedErrorAndAFailure() {
    OutboxStore broken =
        new OutboxStore() {
          @Override
          public List<OutboxMessage> lockPendingBatch(int batchSize) {
            throw new IllegalStateException("database down");
          }

          @Override
          public void markPublished(UUID id) {}

          @Override
          public void recordFailure(UUID id, int previousAttempts) {}

          @Override
          public long purgePublishedBefore(Instant cutoff) {
            return 0;
          }
        };
    OutboxRelay failing =
        new OutboxRelay(
            broken,
            amqp,
            new TransactionTemplate(mock(PlatformTransactionManager.class)),
            10,
            Duration.ofDays(7),
            Clock.fixed(NOW, ZoneOffset.UTC),
            Duration.ofMinutes(2),
            observations.registry());

    assertThatThrownBy(failing::relayPendingBatch).hasMessage("database down");

    assertThat(observations.named(OutboxRelay.RELAY_OBSERVATION).getFirst().error())
        .isInstanceOf(IllegalStateException.class);
    assertThat(failing.status().consecutiveFailures()).isEqualTo(1);
  }

  @Test
  void anEmptyBatchIsASuccess() {
    relay.relayPendingBatch();

    assertThat(relay.status().lastSuccess()).isEqualTo(NOW);
  }

  @Test
  void theHealthIsDownAtTheThresholdAndUpBefore() {
    doThrow(new AmqpException("broker down"))
        .when(amqp)
        .send(anyString(), anyString(), any(Message.class));
    store.pending.add(row());
    OutboxRelayScheduler scheduler =
        new OutboxRelayScheduler(List.of(relay), Duration.ofSeconds(1), Duration.ofHours(1));
    OutboxHealthIndicator health = new OutboxHealthIndicator(scheduler, 2);

    assertThat(health.health().getStatus()).isEqualTo(Status.UP);
    relay.relayPendingBatch();
    Health struggling = health.health();
    assertThat(struggling.getStatus()).isEqualTo(Status.UP);
    assertThat(struggling.getDetails()).containsEntry("failureThreshold", 2);
    @SuppressWarnings("unchecked")
    Map<String, Map<String, Object>> stores =
        (Map<String, Map<String, Object>>) struggling.getDetails().get("stores");
    assertThat(stores.get("InMemoryOutboxStore"))
        .containsEntry("consecutiveFailures", 1)
        .containsEntry("lastSuccess", "never");

    relay.relayPendingBatch();

    assertThat(health.health().getStatus()).isEqualTo(Status.DOWN);
  }

  private static OutboxMessage row() {
    return new OutboxMessage(
        UUID.randomUUID(),
        "platform.events",
        "evt.wallet.debited",
        UUID.randomUUID().toString(),
        "{}".getBytes(StandardCharsets.UTF_8),
        0,
        Map.of());
  }
}
