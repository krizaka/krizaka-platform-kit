package com.krizaka.messaging.dedup;

import java.time.Instant;
import java.util.Objects;

/**
 * Consumer-side idempotency for at-least-once delivery: a message seen twice is processed once, and
 * a message whose processing failed is seen again.
 *
 * <p>The two halves are both required, and each implementation of this pattern that was found in
 * the wild held at most one of them:
 *
 * <ul>
 *   <li><b>{@link #claim} is atomic.</b> A read followed by a write ("does it exist? then insert")
 *       lets two concurrent deliveries of one message both proceed — under exactly the condition
 *       deduplication exists for, because a broker redelivery <i>overlaps</i> a slow first attempt
 *       rather than following it.
 *   <li><b>{@link #release} undoes a claim when the handler fails.</b> Without it the failed
 *       attempt's own claim refuses the redelivery, and a double-process becomes a silent loss.
 * </ul>
 *
 * <p>The listener shape — {@link #processOnce} holds both halves, so a listener cannot forget the
 * second:
 *
 * <pre>{@code
 * @RabbitListener(queues = "krizaka.billing.settlements")
 * void onOutcome(JobOutcome event, @Header(AmqpHeaders.MESSAGE_ID) String messageId) {
 *   dedup.processOnce("billing.settlement", messageId, () -> ledger.settle(event));
 * }
 * }</pre>
 *
 * <p>{@link #claim} and {@link #release} stay public for a handler that must decide between them
 * itself (a claim taken in one transaction, released in another).
 *
 * <p>A message with no id is always claimable: a producer that sent no id asked for no
 * deduplication, and dropping its messages would be the worse failure.
 */
public interface MessageDedup {

  /**
   * Claims a message for processing, atomically.
   *
   * @param consumer who is processing — one message may legitimately be processed once per consumer
   * @param messageId the broker's message id; {@code null} or blank is always claimable
   * @return {@code true} when this caller may process the message, {@code false} when it was
   *     already claimed
   */
  boolean claim(String consumer, String messageId);

  /**
   * Gives a claim back after processing failed, so the redelivery is processed.
   *
   * @param consumer who was processing
   * @param messageId the message to make claimable again; {@code null} or blank is a no-op
   */
  void release(String consumer, String messageId);

  /**
   * Runs {@code handler} unless this consumer already processed the message: {@link #claim} first,
   * and {@link #release} when the handler fails, before the failure is rethrown.
   *
   * <p>This is the shape every listener needs, written once: a listener that claims without
   * releasing turns a failed attempt into a refused redelivery — a silent loss. The failure is
   * rethrown unchanged, so the container's retry and dead-letter policy still applies.
   *
   * @param consumer who is processing
   * @param messageId the broker's message id; {@code null} or blank always runs the handler
   * @param handler the processing, run at most once per successful claim
   * @return {@code true} when the handler ran and returned, {@code false} when the message had
   *     already been claimed
   */
  default boolean processOnce(String consumer, String messageId, Runnable handler) {
    Objects.requireNonNull(handler, "handler");
    if (!claim(consumer, messageId)) {
      return false;
    }
    try {
      handler.run();
    } catch (RuntimeException | Error failed) {
      release(consumer, messageId);
      throw failed;
    }
    return true;
  }

  /**
   * Forgets the claims recorded before {@code cutoff}.
   *
   * <p>A claim is only useful while a redelivery is still possible; once the broker cannot deliver
   * the message again, the row is dead weight. Call it from housekeeping with a cutoff well beyond
   * the broker's longest redelivery window (days, not minutes).
   *
   * @param cutoff claims recorded before this instant are forgotten
   * @return how many claims were forgotten
   */
  long purgeClaimedBefore(Instant cutoff);
}
