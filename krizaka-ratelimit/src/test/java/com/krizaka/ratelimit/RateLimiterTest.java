package com.krizaka.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;

class RateLimiterTest {

  /** A clock the test moves. */
  private static final class MovingClock extends Clock {
    private Instant now = Instant.parse("2026-10-21T12:00:00Z");

    void advance(Duration d) {
      now = now.plus(d);
    }

    @Override
    public ZoneId getZone() {
      return ZoneId.of("UTC");
    }

    @Override
    public Clock withZone(ZoneId zone) {
      return this;
    }

    @Override
    public Instant instant() {
      return now;
    }
  }

  private final MovingClock clock = new MovingClock();

  private RateLimiter limiter(RateLimitMode mode, int limit) {
    return new RateLimiter(mode, limit, 100, clock);
  }

  private static RateLimitKey key(String id) {
    return RateLimitKey.of(id);
  }

  @Test
  void aKeyMayBurstUpToItsLimitThenIsRefusedWithAWait() {
    RateLimiter limiter = limiter(RateLimitMode.ENFORCE, 60);

    for (int i = 0; i < 60; i++) {
      assertThat(limiter.tryAcquire(key("a")).allowed()).as("call " + i).isTrue();
    }
    RateLimitDecision refused = limiter.tryAcquire(key("a"));

    assertThat(refused.allowed()).isFalse();
    assertThat(refused.overLimit()).isTrue();
    assertThat(refused.remaining()).isZero();
    assertThat(refused.limit()).isEqualTo(60);
    assertThat(refused.retryAfterSeconds()).isEqualTo(1);
  }

  @Test
  void remainingCountsDown() {
    RateLimiter limiter = limiter(RateLimitMode.ENFORCE, 10);

    assertThat(limiter.tryAcquire(key("a")).remaining()).isEqualTo(9);
    assertThat(limiter.tryAcquire(key("a")).remaining()).isEqualTo(8);
  }

  @Test
  void theBucketRefillsContinuously() {
    RateLimiter limiter = limiter(RateLimitMode.ENFORCE, 60);
    for (int i = 0; i < 60; i++) {
      limiter.tryAcquire(key("a"));
    }

    clock.advance(Duration.ofMillis(500));
    assertThat(limiter.tryAcquire(key("a")).allowed()).as("half a token").isFalse();
    clock.advance(Duration.ofMillis(500));
    assertThat(limiter.tryAcquire(key("a")).allowed()).as("one token").isTrue();
    assertThat(limiter.tryAcquire(key("a")).allowed()).isFalse();
  }

  @Test
  void theWaitIsWhenATokenWillExist() {
    RateLimiter limiter = limiter(RateLimitMode.ENFORCE, 2);
    limiter.tryAcquire(key("a"));
    limiter.tryAcquire(key("a"));

    assertThat(limiter.tryAcquire(key("a")).retryAfterSeconds()).isEqualTo(30);
    clock.advance(Duration.ofSeconds(10));
    assertThat(limiter.tryAcquire(key("a")).retryAfterSeconds()).isEqualTo(20);
  }

  @Test
  void keysAreIndependent() {
    RateLimiter limiter = limiter(RateLimitMode.ENFORCE, 1);

    assertThat(limiter.tryAcquire(key("a")).allowed()).isTrue();
    assertThat(limiter.tryAcquire(key("a")).allowed()).isFalse();
    assertThat(limiter.tryAcquire(key("b")).allowed()).isTrue();
  }

  @Test
  void aKeyCarriesItsOwnTierAndATierChangeAppliesAtOnce() {
    RateLimiter limiter = limiter(RateLimitMode.ENFORCE, 1);

    assertThat(limiter.tryAcquire(new RateLimitKey("a", 5)).limit()).isEqualTo(5);
    assertThat(limiter.tryAcquire(new RateLimitKey("a", 5)).remaining()).isEqualTo(3);
    assertThat(limiter.tryAcquire(new RateLimitKey("a", 0)).limit())
        .as("a limit below 1 falls back to the default")
        .isEqualTo(1);
  }

  @Test
  void observeCountsAndFlagsButNeverRefuses() {
    RateLimiter limiter = limiter(RateLimitMode.OBSERVE, 1);
    limiter.tryAcquire(key("a"));

    RateLimitDecision over = limiter.tryAcquire(key("a"));

    assertThat(over.allowed()).isTrue();
    assertThat(over.overLimit()).isTrue();
    assertThat(over.retryAfterSeconds()).isPositive();
  }

  @Test
  void offKeepsNothing() {
    RateLimiter limiter = limiter(RateLimitMode.OFF, 1);

    for (int i = 0; i < 5; i++) {
      assertThat(limiter.tryAcquire(key("a")).allowed()).isTrue();
    }
    assertThat(limiter.active()).isFalse();
    assertThat(limiter.trackedKeys()).isZero();
  }

  @Test
  void aFullTableForgetsIdleKeysButNeverABusyOne() {
    RateLimiter limiter = new RateLimiter(RateLimitMode.ENFORCE, 2, 2, clock);
    limiter.tryAcquire(key("busy"));
    limiter.tryAcquire(key("busy"));
    limiter.tryAcquire(key("idle"));
    clock.advance(Duration.ofMinutes(1));
    limiter.tryAcquire(key("busy"));
    limiter.tryAcquire(key("busy"));

    limiter.tryAcquire(key("newcomer"));

    assertThat(limiter.trackedKeys()).isLessThanOrEqualTo(2);
    assertThat(limiter.tryAcquire(key("busy")).allowed())
        .as("a flood of new keys never resets a busy key's debt")
        .isFalse();
  }

  @Test
  void keysAreNeverHeldInClear() {
    RateLimiter limiter = limiter(RateLimitMode.ENFORCE, 5);
    limiter.tryAcquire(key("oz_secret-api-key"));

    assertThat(limiter.trackedKeys()).isEqualTo(1);
    assertThat(limiter.toString()).doesNotContain("oz_secret-api-key");
  }

  @Test
  void refusesASillyConfiguration() {
    assertThatThrownBy(() -> new RateLimiter(RateLimitMode.ENFORCE, 0, 10, clock))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new RateLimiter(RateLimitMode.ENFORCE, 10, 0, clock))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
