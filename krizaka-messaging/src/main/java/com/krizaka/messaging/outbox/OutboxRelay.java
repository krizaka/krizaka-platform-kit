package com.krizaka.messaging.outbox;

import java.time.Clock;
import java.time.Duration;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.AmqpTemplate;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Publishes an {@link OutboxStore}'s pending rows to RabbitMQ, each exactly once per success.
 *
 * <p>A batch claims its rows, publishes each as a persistent JSON message carrying its {@code
 * messageId}, its headers and a timestamp, then marks it published — or, when the publish fails,
 * records a failure so the store backs it off. A failure never stops the rest of the batch.
 *
 * <p>With a {@linkplain OutboxStore#leasesRows() leasing} store the claim is a short transaction
 * and every publish and every mark runs outside one: a slow or stalled broker never holds a
 * database connection or a row lock. With an older store the whole batch runs in one transaction,
 * as before.
 *
 * <p>Delivery is at-least-once: a crash between the publish and the commit republishes the row on
 * the next run, with the same {@code messageId}, which consumers deduplicate on ({@link
 * com.krizaka.messaging.dedup.MessageDedup}).
 *
 * <p>Usually scheduled by the auto-configuration; it can also be driven by hand.
 */
public final class OutboxRelay {

  private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);

  private final OutboxStore store;
  private final AmqpTemplate amqp;
  private final TransactionTemplate transactions;
  private final int batchSize;
  private final Duration retention;
  private final Clock clock;
  private final Duration lease;

  // @fixed: how long a claimed row stays invisible to other relays; a safety bound, not a business
  // rule
  private static final Duration DEFAULT_LEASE = Duration.ofMinutes(2);

  /**
   * A relay over one store.
   *
   * @param store the outbox table's owner
   * @param amqp where rows are published
   * @param transactions the transaction each batch runs in
   * @param batchSize the most rows published per batch
   * @param retention how long published rows are kept before {@link #purgePublished} deletes them
   * @param clock the source of the purge cutoff
   */
  public OutboxRelay(
      OutboxStore store,
      AmqpTemplate amqp,
      TransactionTemplate transactions,
      int batchSize,
      Duration retention,
      Clock clock) {
    this(store, amqp, transactions, batchSize, retention, clock, DEFAULT_LEASE);
  }

  /**
   * A relay over one store, with an explicit lease for {@linkplain OutboxStore#leasesRows()
   * leasing} stores.
   *
   * @param lease how long a claimed row stays invisible to other relays (longer than a batch takes)
   */
  public OutboxRelay(
      OutboxStore store,
      AmqpTemplate amqp,
      TransactionTemplate transactions,
      int batchSize,
      Duration retention,
      Clock clock,
      Duration lease) {
    this.lease = Objects.requireNonNull(lease, "lease");
    this.store = Objects.requireNonNull(store, "store");
    this.amqp = Objects.requireNonNull(amqp, "amqp");
    this.transactions = Objects.requireNonNull(transactions, "transactions");
    if (batchSize < 1) {
      throw new IllegalArgumentException("batchSize must be positive");
    }
    this.batchSize = batchSize;
    this.retention = Objects.requireNonNull(retention, "retention");
    this.clock = Objects.requireNonNull(clock, "clock");
  }

  /**
   * Publishes one batch of pending rows.
   *
   * @return how many rows were published
   */
  public int relayPendingBatch() {
    if (store.leasesRows()) {
      return relayLeased();
    }
    Integer published = transactions.execute(status -> relay(store.lockPendingBatch(batchSize)));
    return published == null ? 0 : published;
  }

  /**
   * Deletes the published rows older than the retention window.
   *
   * @return how many rows were deleted
   */
  public long purgePublished() {
    Long purged =
        transactions.execute(
            status -> store.purgePublishedBefore(clock.instant().minus(retention)));
    long count = purged == null ? 0 : purged;
    if (count > 0) {
      log.info("Purged {} published outbox rows from {}", count, storeName());
    }
    return count;
  }

  private int relayLeased() {
    List<OutboxMessage> batch = transactions.execute(status -> store.leaseBatch(batchSize, lease));
    int published = 0;
    for (OutboxMessage message : batch == null ? List.<OutboxMessage>of() : batch) {
      try {
        amqp.send(message.exchange(), message.routingKey(), toAmqp(message, clock));
      } catch (RuntimeException e) {
        logPublishFailure(message, e);
        inTransaction(
            () -> store.recordFailure(message.id(), message.attempts()), message, "failure");
        continue;
      }
      published +=
          inTransaction(() -> store.markPublished(message.id()), message, "published") ? 1 : 0;
    }
    return published;
  }

  /**
   * A mark that cannot be written leaves the lease to expire: the row is republished, never lost.
   */
  private boolean inTransaction(Runnable write, OutboxMessage message, String what) {
    try {
      transactions.executeWithoutResult(status -> write.run());
      return true;
    } catch (RuntimeException e) {
      log.warn(
          "Outbox row {} could not be marked {} — its lease will expire", message.id(), what, e);
      return false;
    }
  }

  private void logPublishFailure(OutboxMessage message, RuntimeException e) {
    log.warn(
        "Outbox publish failed for {} ({} -> {}), attempt {} — backing off",
        message.id(),
        message.exchange(),
        message.routingKey(),
        message.attempts() + 1,
        e);
  }

  private int relay(List<OutboxMessage> batch) {
    int published = 0;
    for (OutboxMessage message : batch) {
      try {
        amqp.send(message.exchange(), message.routingKey(), toAmqp(message, clock));
        store.markPublished(message.id());
        published++;
      } catch (RuntimeException e) {
        logPublishFailure(message, e);
        store.recordFailure(message.id(), message.attempts());
      }
    }
    return published;
  }

  static Message toAmqp(OutboxMessage message, Clock clock) {
    MessageProperties properties = new MessageProperties();
    properties.setContentType(MessageProperties.CONTENT_TYPE_JSON);
    properties.setDeliveryMode(MessageDeliveryMode.PERSISTENT);
    properties.setPriority(0);
    if (message.messageId() != null) {
      properties.setMessageId(message.messageId());
    }
    message.headers().forEach(properties::setHeader);
    // The publish time; when the event happened travels in its own header (kz-occurred-at).
    properties.setTimestamp(Date.from(clock.instant()));
    return new Message(message.body(), properties);
  }

  String storeName() {
    return store.getClass().getSimpleName();
  }
}
