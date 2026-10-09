package com.krizaka.messaging.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.AmqpTemplate;
import org.springframework.amqp.core.Message;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

class OutboxRelaySchedulerTest {

  @Test
  void relaysOnAFixedDelayUntilStopped() {
    InMemoryOutboxStore store = new InMemoryOutboxStore();
    store.pending.add(
        new OutboxMessage(
            UUID.randomUUID(), "x", "k", "m", "{}".getBytes(StandardCharsets.UTF_8), 0));
    AmqpTemplate amqp = mock(AmqpTemplate.class);
    OutboxRelay relay =
        new OutboxRelay(
            store,
            amqp,
            new TransactionTemplate(mock(PlatformTransactionManager.class)),
            10,
            Duration.ofDays(7),
            Clock.systemUTC());
    OutboxRelayScheduler scheduler =
        new OutboxRelayScheduler(List.of(relay), Duration.ofMillis(10), Duration.ofHours(1));

    scheduler.start();
    try {
      verify(amqp, timeout(2_000)).send(any(String.class), any(String.class), any(Message.class));
      assertThat(scheduler.isRunning()).isTrue();
    } finally {
      scheduler.stop();
    }
    assertThat(scheduler.isRunning()).isFalse();
  }

  @Test
  void withNoRelaysItNeverStarts() {
    OutboxRelayScheduler scheduler =
        new OutboxRelayScheduler(List.of(), Duration.ofMillis(10), Duration.ofHours(1));
    scheduler.start();
    assertThat(scheduler.isRunning()).isFalse();
  }
}
