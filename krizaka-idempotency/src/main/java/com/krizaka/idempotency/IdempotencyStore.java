package com.krizaka.idempotency;

import java.time.Instant;
import java.util.Optional;

/**
 * Where keys and the responses they produced are kept.
 *
 * <p>The contract that makes the guarantee: {@link #claim} either records the key — and then blocks
 * every other claim of the same key until {@link #complete} or {@link #abandon} (or, for a
 * {@linkplain #transactional() transactional} store, the end of the transaction) — or returns what
 * the key recorded. Two concurrent requests with one key therefore take turns: the second waits and
 * replays.
 */
public interface IdempotencyStore {

  /**
   * Claims a key, or reads what it recorded.
   *
   * @param scope the scope
   * @param subject the subject
   * @param key the key
   * @param fingerprint the request's fingerprint
   * @param now the claim time
   * @param expiredBefore a key recorded before this instant is expired and claimed again
   * @return empty when the key is now claimed by the caller; otherwise what it recorded
   */
  Optional<Recorded> claim(
      String scope,
      String subject,
      String key,
      String fingerprint,
      Instant now,
      Instant expiredBefore);

  /**
   * Stores the response of a claimed key.
   *
   * @param scope the scope
   * @param subject the subject
   * @param key the key
   * @param response the response, as JSON
   */
  void complete(String scope, String subject, String key, String response);

  /**
   * Gives a claimed key back after the action failed, so a retry runs it for real. A transactional
   * store never needs it: the rollback forgets the claim.
   *
   * @param scope the scope
   * @param subject the subject
   * @param key the key
   */
  void abandon(String scope, String subject, String key);

  /**
   * Forgets the keys recorded before {@code cutoff}.
   *
   * @param cutoff the oldest kept
   * @return how many were forgotten
   */
  long purgeBefore(Instant cutoff);

  /**
   * Whether {@link #claim} and {@link #complete} join the caller's transaction (a failed action is
   * then forgotten by the rollback, never by {@link #abandon}).
   *
   * @return {@code true} for a database store
   */
  default boolean transactional() {
    return false;
  }

  /**
   * What a key recorded.
   *
   * @param fingerprint the fingerprint of the request that ran
   * @param response its response, as JSON
   */
  record Recorded(String fingerprint, String response) {}
}
