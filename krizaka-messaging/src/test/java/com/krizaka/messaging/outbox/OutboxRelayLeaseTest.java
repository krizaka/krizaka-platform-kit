package com.krizaka.messaging.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.AmqpTemplate;
import org.springframework.amqp.core.Message;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

/** platform-kit#45: a broker call must never run inside a database transaction. */
class OutboxRelayLeaseTest {

  /** Tells whether a transaction is open, the way a connection held by the relay would be. */
  private static final class WatchingTransactions implements PlatformTransactionManager {
    final AtomicBoolean open = new AtomicBoolean();
    int started;

    @Override
    public TransactionStatus getTransaction(TransactionDefinition definition) {
      open.set(true);
      started++;
      return new SimpleTransactionStatus();
    }

    @Override
    public void commit(TransactionStatus status) {
      open.set(false);
    }

    @Override
    public void rollback(TransactionStatus status) {
      open.set(false);
    }
  }

  private static final class LeasingStore implements OutboxStore {
    final List<OutboxMessage> due = new ArrayList<>();
    final List<UUID> published = new ArrayList<>();
    final List<UUID> failed = new ArrayList<>();
    Duration leasedFor;

    @Override
    public boolean leasesRows() {
      return true;
    }

    @Override
    public List<OutboxMessage> leaseBatch(int batchSize, Duration lease) {
      leasedFor = lease;
      return List.copyOf(due.subList(0, Math.min(batchSize, due.size())));
    }

    @Override
    public List<OutboxMessage> lockPendingBatch(int batchSize) {
      throw new AssertionError("a leasing store is claimed with leaseBatch");
    }

    @Override
    public void markPublished(UUID id) {
      published.add(id);
    }

    @Override
    public void recordFailure(UUID id, int previousAttempts) {
      failed.add(id);
    }

    @Override
    public long purgePublishedBefore(Instant cutoff) {
      return 0;
    }
  }

  private final WatchingTransactions manager = new WatchingTransactions();
  private final LeasingStore store = new LeasingStore();
  private final AmqpTemplate amqp = mock(AmqpTemplate.class);
  private final OutboxRelay relay =
      new OutboxRelay(
          store,
          amqp,
          new TransactionTemplate(manager),
          10,
          Duration.ofDays(7),
          Clock.fixed(Instant.parse("2026-10-20T12:00:00Z"), ZoneOffset.UTC),
          Duration.ofMinutes(3));

  @Test
  void publishesOutsideAnyTransactionAndClaimsForTheLease() {
    store.due.add(row("m-1"));
    store.due.add(row("m-2"));
    List<Boolean> openAtSend = new ArrayList<>();
    doAnswer(
            call -> {
              openAtSend.add(manager.open.get());
              return null;
            })
        .when(amqp)
        .send(any(String.class), any(String.class), any(Message.class));

    assertThat(relay.relayPendingBatch()).isEqualTo(2);

    assertThat(openAtSend).containsExactly(false, false);
    assertThat(store.leasedFor).isEqualTo(Duration.ofMinutes(3));
    assertThat(store.published).hasSize(2);
  }

  @Test
  void aFailedPublishIsRecordedAndDoesNotStopTheBatch() {
    OutboxMessage bad = row("bad");
    store.due.add(bad);
    store.due.add(row("good"));
    doThrow(new AmqpException("broker down"))
        .doNothing()
        .when(amqp)
        .send(any(String.class), any(String.class), any(Message.class));

    assertThat(relay.relayPendingBatch()).isEqualTo(1);

    assertThat(store.failed).containsExactly(bad.id());
    assertThat(store.published).hasSize(1);
  }

  private static OutboxMessage row(String messageId) {
    return new OutboxMessage(
        UUID.randomUUID(),
        "platform.events",
        "evt.test",
        messageId,
        "{}".getBytes(StandardCharsets.UTF_8),
        0,
        Map.of());
  }
}
