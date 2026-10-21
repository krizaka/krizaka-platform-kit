package com.krizaka.messaging.outbox;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
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
 *
 * <p>Observed: each batch is a {@value #RELAY_OBSERVATION} observation and each publish a {@value
 * #PUBLISH_OBSERVATION} one (a timer and a span each, with Spring Boot's Micrometer support), and
 * {@link #status()} tells the {@code outbox} health indicator how the relay is doing.
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
  private final ObservationRegistry observations;
  private final AtomicReference<Instant> lastSuccess = new AtomicReference<>();
  private final AtomicInteger consecutiveFailures = new AtomicInteger();
  private final AtomicReference<String> lastError = new AtomicReference<>();

  /** The observation of one batch: a timer and a span, tagged with the store. */
  public static final String RELAY_OBSERVATION = "krizaka.outbox.relay";

  /** The observation of one publish: a timer and a span, tagged with the exchange and the key. */
  public static final String PUBLISH_OBSERVATION = "krizaka.outbox.publish";

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
    this(store, amqp, transactions, batchSize, retention, clock, lease, ObservationRegistry.NOOP);
  }

  /**
   * A relay over one store, observed.
   *
   * @param store the outbox table's owner
   * @param amqp where rows are published
   * @param transactions the transaction each batch runs in
   * @param batchSize the most rows published per batch
   * @param retention how long published rows are kept before {@link #purgePublished} deletes them
   * @param clock the source of the purge cutoff and of the status times
   * @param lease how long a claimed row stays invisible to other relays (leasing stores)
   * @param observations where batches and publishes are observed; {@link ObservationRegistry#NOOP}
   *     to observe nothing
   */
  public OutboxRelay(
      OutboxStore store,
      AmqpTemplate amqp,
      TransactionTemplate transactions,
      int batchSize,
      Duration retention,
      Clock clock,
      Duration lease,
      ObservationRegistry observations) {
    this.observations = Objects.requireNonNull(observations, "observations");
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
    Observation batch =
        Observation.createNotStarted(RELAY_OBSERVATION, observations)
            .lowCardinalityKeyValue("krizaka.outbox.store", storeName())
            .start();
    try (Observation.Scope scope = batch.openScope()) {
      int published;
      if (store.leasesRows()) {
        published = relayLeased();
      } else {
        Integer count = transactions.execute(status -> relay(store.lockPendingBatch(batchSize)));
        published = count == null ? 0 : count;
      }
      batch.highCardinalityKeyValue("krizaka.outbox.published", Integer.toString(published));
      return published;
    } catch (RuntimeException e) {
      failed(e);
      batch.error(e);
      throw e;
    } finally {
      batch.stop();
    }
  }

  /**
   * How the relay is doing, for the {@code outbox} health indicator.
   *
   * @return the store, the last time a row was published or a batch found nothing to do, and the
   *     failures (batches that threw, publishes that failed) since
   */
  public Status status() {
    return new Status(storeName(), lastSuccess.get(), consecutiveFailures.get(), lastError.get());
  }

  /**
   * A relay's state.
   *
   * @param store the store's simple class name
   * @param lastSuccess the last time a row was published or a batch completed with nothing to
   *     publish; {@code null} before the first
   * @param consecutiveFailures batches that threw and publishes that failed since the last success
   * @param lastError the type of the last failure, never its message (it may carry data)
   */
  public record Status(
      String store, Instant lastSuccess, int consecutiveFailures, String lastError) {}

  private void succeeded() {
    lastSuccess.set(clock.instant());
    consecutiveFailures.set(0);
  }

  private void failed(RuntimeException e) {
    consecutiveFailures.incrementAndGet();
    lastError.set(e.getClass().getName());
  }

  /** Publishes one row, observed; a failure is recorded and rethrown. */
  private void publish(OutboxMessage message) {
    try {
      Observation.createNotStarted(PUBLISH_OBSERVATION, observations)
          .lowCardinalityKeyValue("messaging.destination.name", message.exchange())
          .lowCardinalityKeyValue(
              "messaging.rabbitmq.destination.routing_key", message.routingKey())
          .observe(
              () -> amqp.send(message.exchange(), message.routingKey(), toAmqp(message, clock)));
    } catch (RuntimeException e) {
      failed(e);
      throw e;
    }
    succeeded();
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
    if (batch == null || batch.isEmpty()) {
      succeeded();
      return 0;
    }
    int published = 0;
    for (OutboxMessage message : batch) {
      try {
        publish(message);
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
    if (batch.isEmpty()) {
      succeeded();
      return 0;
    }
    int published = 0;
    for (OutboxMessage message : batch) {
      try {
        publish(message);
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
