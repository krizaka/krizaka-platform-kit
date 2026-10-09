package com.krizaka.messaging.dedup;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Claims held in memory, for a service that has no database.
 *
 * <p>The claim is atomic ({@link ConcurrentHashMap#putIfAbsent}) and kept for a retention window,
 * so it guards against a broker's redeliveries and a producer's double publish — but <b>not</b>
 * against a duplicate that arrives after a restart, nor one delivered to another instance. When
 * that matters, use {@link JdbcMessageDedup}.
 *
 * <p>Memory is bounded: once {@code maxClaims} is reached, claims older than the retention window
 * are evicted before a new one is recorded.
 */
public final class InMemoryMessageDedup implements MessageDedup {

  /** The default retention window. */
  public static final Duration DEFAULT_RETENTION = Duration.ofHours(24);

  /** The default number of claims kept before old ones are evicted. */
  public static final int DEFAULT_MAX_CLAIMS = 100_000;

  private final Map<String, Instant> claims = new ConcurrentHashMap<>();
  private final Clock clock;
  private final Duration retention;
  private final int maxClaims;

  /** In-memory claims with the default retention and bound. */
  public InMemoryMessageDedup() {
    this(Clock.systemUTC(), DEFAULT_RETENTION, DEFAULT_MAX_CLAIMS);
  }

  /**
   * In-memory claims with an explicit clock, retention and bound.
   *
   * @param clock the source of claim times
   * @param retention how long a claim is remembered once the bound is reached
   * @param maxClaims the number of claims kept before old ones are evicted
   */
  public InMemoryMessageDedup(Clock clock, Duration retention, int maxClaims) {
    this.clock = Objects.requireNonNull(clock, "clock");
    this.retention = Objects.requireNonNull(retention, "retention");
    if (retention.isNegative() || retention.isZero() || maxClaims < 1) {
      throw new IllegalArgumentException("retention and maxClaims must be positive");
    }
    this.maxClaims = maxClaims;
  }

  @Override
  public boolean claim(String consumer, String messageId) {
    if (messageId == null || messageId.isBlank()) {
      return true;
    }
    Instant now = clock.instant();
    if (claims.size() >= maxClaims) {
      Instant horizon = now.minus(retention);
      claims.values().removeIf(claimedAt -> claimedAt.isBefore(horizon));
    }
    return claims.putIfAbsent(key(consumer, messageId), now) == null;
  }

  @Override
  public void release(String consumer, String messageId) {
    if (messageId == null || messageId.isBlank()) {
      return;
    }
    claims.remove(key(consumer, messageId));
  }

  @Override
  public long purgeClaimedBefore(Instant cutoff) {
    Objects.requireNonNull(cutoff, "cutoff");
    int before = claims.size();
    claims.values().removeIf(claimedAt -> claimedAt.isBefore(cutoff));
    return (long) before - claims.size();
  }

  private static String key(String consumer, String messageId) {
    return Objects.requireNonNull(consumer, "consumer") + '\u0000' + messageId;
  }
}
