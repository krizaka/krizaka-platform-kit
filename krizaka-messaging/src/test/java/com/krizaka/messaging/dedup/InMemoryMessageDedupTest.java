package com.krizaka.messaging.dedup;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class InMemoryMessageDedupTest {

  private final InMemoryMessageDedup dedup = new InMemoryMessageDedup();

  @Test
  void aMessageIsClaimedOncePerConsumer() {
    assertThat(dedup.claim("notifications", "m-1")).isTrue();
    assertThat(dedup.claim("notifications", "m-1")).isFalse();
    assertThat(dedup.claim("audit", "m-1")).isTrue();
  }

  @Test
  void aReleasedClaimCanBeClaimedAgain() {
    dedup.claim("notifications", "m-1");
    dedup.release("notifications", "m-1");
    assertThat(dedup.claim("notifications", "m-1")).isTrue();
  }

  @Test
  void aMessageWithoutAnIdIsAlwaysClaimable() {
    assertThat(dedup.claim("notifications", null)).isTrue();
    assertThat(dedup.claim("notifications", "  ")).isTrue();
  }

  @Test
  void consumerAndIdCannotCollideByConcatenation() {
    assertThat(dedup.claim("a:b", "c")).isTrue();
    assertThat(dedup.claim("a", "b:c")).isTrue();
  }

  @Test
  void oldClaimsAreEvictedOnceTheBoundIsReached() {
    AtomicReference<Instant> now = new AtomicReference<>(Instant.parse("2026-01-01T00:00:00Z"));
    Clock clock =
        new Clock() {
          @Override
          public Instant instant() {
            return now.get();
          }

          @Override
          public ZoneOffset getZone() {
            return ZoneOffset.UTC;
          }

          @Override
          public Clock withZone(java.time.ZoneId zone) {
            return this;
          }
        };
    InMemoryMessageDedup bounded = new InMemoryMessageDedup(clock, Duration.ofHours(1), 2);
    bounded.claim("c", "old-1");
    bounded.claim("c", "old-2");

    now.set(now.get().plus(Duration.ofHours(2)));
    assertThat(bounded.claim("c", "new")).isTrue();

    assertThat(bounded.claim("c", "old-1")).as("evicted, so claimable again").isTrue();
  }

  @Test
  void housekeepingForgetsClaimsRecordedBeforeTheCutoff() {
    dedup.claim("notifications", "m-1");

    assertThat(dedup.purgeClaimedBefore(Instant.now().plus(Duration.ofSeconds(1)))).isEqualTo(1);
    assertThat(dedup.claim("notifications", "m-1")).isTrue();
  }

  @Test
  void refusesANonPositiveConfiguration() {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new InMemoryMessageDedup(Clock.systemUTC(), Duration.ZERO, 10));
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new InMemoryMessageDedup(Clock.systemUTC(), Duration.ofHours(1), 0));
  }
}
