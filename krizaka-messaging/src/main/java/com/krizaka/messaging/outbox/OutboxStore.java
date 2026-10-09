package com.krizaka.messaging.outbox;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * The storage side of a transactional outbox, implemented by the application that owns the table.
 *
 * <p>The application appends rows in the same transaction as the state change they announce; the
 * {@link OutboxRelay} drains them. The relay owns <i>when</i> and <i>how</i> a row is published;
 * the store owns the schema, the payload format and the back-off.
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
