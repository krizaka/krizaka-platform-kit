package com.krizaka.messaging.outbox;

import java.time.Clock;
import java.time.Duration;
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
 * <p>One batch runs in one transaction: the rows are claimed, each is published as a persistent
 * JSON message carrying its {@code messageId}, then marked published — or, when the publish fails,
 * recorded as a failure so the store backs it off. A failure never stops the rest of the batch.
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

  private int relay(List<OutboxMessage> batch) {
    int published = 0;
    for (OutboxMessage message : batch) {
      try {
        amqp.send(message.exchange(), message.routingKey(), toAmqp(message));
        store.markPublished(message.id());
        published++;
      } catch (RuntimeException e) {
        log.warn(
            "Outbox publish failed for {} ({} -> {}), attempt {} — backing off",
            message.id(),
            message.exchange(),
            message.routingKey(),
            message.attempts() + 1,
            e);
        store.recordFailure(message.id(), message.attempts());
      }
    }
    return published;
  }

  private static Message toAmqp(OutboxMessage message) {
    MessageProperties properties = new MessageProperties();
    properties.setContentType(MessageProperties.CONTENT_TYPE_JSON);
    properties.setDeliveryMode(MessageDeliveryMode.PERSISTENT);
    properties.setPriority(0);
    if (message.messageId() != null) {
      properties.setMessageId(message.messageId());
    }
    return new Message(message.body(), properties);
  }

  String storeName() {
    return store.getClass().getSimpleName();
  }
}
