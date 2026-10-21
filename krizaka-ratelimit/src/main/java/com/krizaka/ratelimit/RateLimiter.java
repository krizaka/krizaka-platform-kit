package com.krizaka.ratelimit;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.util.HexFormat;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Requests per minute per key: a token bucket for each key, sized by the key's limit.
 *
 * <p>A bucket holds up to one minute's worth of calls (a client may burst) and refills
 * continuously. A refused call is told how long to wait. Keys are held as SHA-256 digests, never in
 * clear. A full table forgets idle buckets (those already refilled to capacity — forgetting them
 * changes nothing) and never flushes a busy one, so a flood of new keys cannot reset another key's
 * debt.
 *
 * <p>State is in memory, per instance: with N instances a key may spend N times its limit. A shared
 * store is the move when the service scales out; until then, say so and size the limit for it.
 */
public final class RateLimiter {

  private static final double EPSILON = 1e-9;

  private final RateLimitMode mode;
  private final int defaultLimit;
  private final Clock clock;
  private final int maxKeys;
  private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

  /**
   * A limiter.
   *
   * @param mode how the limit is applied
   * @param defaultLimit requests per minute for a key that brings none, at least 1
   * @param maxKeys how many keys are tracked before idle ones are forgotten, at least 1
   * @param clock the clock
   */
  public RateLimiter(RateLimitMode mode, int defaultLimit, int maxKeys, Clock clock) {
    if (defaultLimit < 1) {
      throw new IllegalArgumentException("the default requests per minute must be at least 1");
    }
    if (maxKeys < 1) {
      throw new IllegalArgumentException("the number of tracked keys must be at least 1");
    }
    this.mode = Objects.requireNonNull(mode, "mode");
    this.defaultLimit = defaultLimit;
    this.maxKeys = maxKeys;
    this.clock = Objects.requireNonNull(clock, "clock");
  }

  /**
   * Whether anything is kept at all.
   *
   * @return {@code false} in {@link RateLimitMode#OFF}
   */
  public boolean active() {
    return mode != RateLimitMode.OFF;
  }

  /**
   * The mode.
   *
   * @return how the limit is applied
   */
  public RateLimitMode mode() {
    return mode;
  }

  /**
   * Counts one call.
   *
   * @param key who is calling
   * @return the verdict
   */
  public RateLimitDecision tryAcquire(RateLimitKey key) {
    Integer own = key.requestsPerMinute();
    int limit = own == null || own < 1 ? defaultLimit : own;
    if (mode == RateLimitMode.OFF) {
      return new RateLimitDecision(true, false, limit, limit, 0);
    }
    String digest = digest(key.id());
    Bucket bucket = buckets.get(digest);
    if (bucket == null) {
      if (buckets.size() >= maxKeys) {
        sweepIdle();
      }
      bucket = buckets.computeIfAbsent(digest, k -> new Bucket(limit, clock.millis()));
    }
    RateLimitDecision taken = bucket.take(limit, clock.millis());
    return mode == RateLimitMode.ENFORCE
        ? taken
        : new RateLimitDecision(
            true, taken.overLimit(), taken.limit(), taken.remaining(), taken.retryAfterSeconds());
  }

  /**
   * How many keys are tracked now.
   *
   * @return the number of buckets
   */
  public int trackedKeys() {
    return buckets.size();
  }

  private void sweepIdle() {
    long now = clock.millis();
    buckets.entrySet().removeIf(e -> e.getValue().isFull(now));
  }

  private static String digest(String id) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256").digest(id.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException impossible) {
      throw new IllegalStateException("SHA-256 is part of every JRE", impossible);
    }
  }

  /** A token bucket sized by the limit of the moment: refilled at {@code limit / 60} a second. */
  private static final class Bucket {
    private double tokens;
    private long lastMillis;
    private int limit;

    Bucket(int limit, long nowMillis) {
      this.limit = limit;
      this.tokens = limit;
      this.lastMillis = nowMillis;
    }

    synchronized RateLimitDecision take(int newLimit, long nowMillis) {
      refill(newLimit, nowMillis);
      if (tokens >= 1.0 - EPSILON) {
        tokens = Math.max(0.0, tokens - 1.0);
        return new RateLimitDecision(true, false, limit, (int) Math.floor(tokens + EPSILON), 0);
      }
      double millisUntilToken = (1.0 - tokens) * 60_000.0 / limit;
      long seconds = Math.max(1L, (long) Math.ceil(millisUntilToken / 1000.0 - EPSILON));
      return new RateLimitDecision(false, true, limit, 0, seconds);
    }

    synchronized boolean isFull(long nowMillis) {
      refill(limit, nowMillis);
      return tokens >= limit - EPSILON;
    }

    private void refill(int newLimit, long nowMillis) {
      long elapsed = Math.max(0L, nowMillis - lastMillis);
      tokens = Math.min(newLimit, tokens + elapsed * (double) newLimit / 60_000.0);
      limit = newLimit;
      lastMillis = nowMillis;
    }
  }
}
