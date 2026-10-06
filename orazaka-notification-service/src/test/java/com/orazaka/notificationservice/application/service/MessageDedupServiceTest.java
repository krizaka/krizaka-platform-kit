package com.orazaka.notificationservice.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class MessageDedupServiceTest {

  private final MessageDedupService dedup =
      new MessageDedupService(Clock.fixed(Instant.parse("2026-10-06T00:00:00Z"), ZoneOffset.UTC));

  @Test
  void claimsOncePerConsumerAndMessage() {
    assertThat(dedup.claim("c1", "m1")).isTrue();
    assertThat(dedup.claim("c1", "m1")).isFalse();
    assertThat(dedup.claim("c2", "m1")).isTrue();
  }

  @Test
  void releaseMakesAFailedMessageClaimableAgain() {
    assertThat(dedup.claim("c1", "m1")).isTrue();
    dedup.release("c1", "m1");

    assertThat(dedup.claim("c1", "m1")).isTrue();
  }

  @Test
  void aMessageWithoutIdIsAlwaysProcessed() {
    assertThat(dedup.claim("c1", null)).isTrue();
    assertThat(dedup.claim("c1", " ")).isTrue();
    dedup.release("c1", null);
  }
}
