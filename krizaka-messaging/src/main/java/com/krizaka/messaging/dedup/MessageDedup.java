package com.krizaka.messaging.dedup;

import java.time.Instant;

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
 * <p>The listener shape:
 *
 * <pre>{@code
 * if (!dedup.claim("billing.settlement", message.getMessageProperties().getMessageId())) {
 *   return; // already processed
 * }
 * try {
 *   handle(message);
 * } catch (RuntimeException e) {
 *   dedup.release("billing.settlement", messageId);
 *   throw e; // nack → redelivered → claimable again
 * }
 * }</pre>
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
