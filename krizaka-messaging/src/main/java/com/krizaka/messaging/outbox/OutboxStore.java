package com.krizaka.messaging.outbox;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * The storage side of a transactional outbox, implemented by the application that owns the table.
 *
 * <p>The application appends rows in the same transaction as the state change they announce — by
 * hand, or through {@link com.krizaka.messaging.event.EventPublisher}, which calls {@link #append};
 * the {@link OutboxRelay} drains them. The relay owns <i>when</i> and <i>how</i> a row is
 * published; the store owns the schema and the back-off.
 *
 * <p>The recommended table (PostgreSQL); the kit never creates it, each context owns its own:
 *
 * <pre>{@code
 * CREATE TABLE wallet_outbox (
 *     id              UUID         PRIMARY KEY,
 *     exchange        VARCHAR(100) NOT NULL,
 *     routing_key     VARCHAR(255) NOT NULL,
 *     message_id      VARCHAR(255) NOT NULL UNIQUE,
 *     payload         JSONB        NOT NULL,
 *     headers         JSONB        NOT NULL DEFAULT '{}'::jsonb,
 *     created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
 *     published_at    TIMESTAMPTZ,
 *     attempts        INT          NOT NULL DEFAULT 0,
 *     next_attempt_at TIMESTAMPTZ  NOT NULL DEFAULT now()
 * );
 * CREATE INDEX wallet_outbox_pending ON wallet_outbox (next_attempt_at) WHERE published_at IS NULL;
 * }</pre>
 *
 * <p>The one invariant an implementation must hold: <b>{@link #lockPendingBatch} claims the rows it
 * returns</b> — {@code SELECT … FOR UPDATE SKIP LOCKED} on PostgreSQL — so that two instances of
 * the relay never publish the same row. A plain {@code SELECT} works with one instance and
 * publishes twice the day the service is scaled.
 *
 * <p>Every method is called inside the transaction the relay opens.
 */
public interface OutboxStore {

  /**
   * Appends a row, in the caller's transaction — the one that writes the state change the row
   * announces. The row is due immediately and has no failed attempt.
   *
   * <p>Store the {@code messageId} and the headers as given: the relay publishes them as they were
   * written, which is what makes a republished row recognisable downstream.
   *
   * <p>The default refuses: a store written before {@code append} existed keeps compiling and
   * relaying, and only an {@link com.krizaka.messaging.event.EventPublisher} over it fails — at its
   * first {@code publish}, with this method named.
   *
   * @param message the row to write
   * @throws UnsupportedOperationException when the store does not implement it
   */
  default void append(NewOutboxMessage message) {
    throw new UnsupportedOperationException(
        getClass().getName()
            + " does not implement OutboxStore.append(NewOutboxMessage): an EventPublisher needs"
            + " it to write events to this outbox. Insert the row (exchange, routing key,"
            + " messageId, body, headers) into the outbox table — see the krizaka-messaging"
            + " README, 'Recommended outbox schema'.");
  }

  /**
   * Claims up to {@code batchSize} rows that are due for publishing, oldest first.
   *
   * @param batchSize the maximum number of rows
   * @return the claimed rows, locked until the relay's transaction ends
   */
  List<OutboxMessage> lockPendingBatch(int batchSize);

  /**
   * Records that a row was published.
   *
   * @param id the row
   */
  void markPublished(UUID id);

  /**
   * Records a failed publish and schedules the next attempt (the back-off is the store's choice).
   *
   * @param id the row
   * @param previousAttempts the attempts recorded before this failure
   */
  void recordFailure(UUID id, int previousAttempts);

  /**
   * Deletes published rows older than {@code cutoff}.
   *
   * @param cutoff rows published before this instant are deleted
   * @return how many rows were deleted
   */
  long purgePublishedBefore(Instant cutoff);
}
