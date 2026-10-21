package com.krizaka.messaging.dedup;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class MessageDedupPurgeSchedulerTest {

  @Test
  void forgetsTheClaimsOlderThanTheRetention() {
    InMemoryMessageDedup dedup = new InMemoryMessageDedup();
    dedup.claim("c", "old");
    Clock later = Clock.fixed(Instant.now().plus(Duration.ofDays(8)), ZoneOffset.UTC);

    long purged =
        new MessageDedupPurgeScheduler(dedup, Duration.ofDays(7), Duration.ofHours(1), later)
            .purge();

    assertThat(purged).isEqualTo(1);
    assertThat(dedup.claim("c", "old")).as("forgotten, so claimable again").isTrue();
  }

  @Test
  void keepsTheClaimsWithinTheRetention() {
    InMemoryMessageDedup dedup = new InMemoryMessageDedup();
    dedup.claim("c", "recent");

    assertThat(
            new MessageDedupPurgeScheduler(
                    dedup, Duration.ofDays(7), Duration.ofHours(1), Clock.systemUTC())
                .purge())
        .isZero();
  }

  @Test
  void scheduledWithTheStoreWithTheConfiguredRetention() {
    new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(MessageDedupAutoConfiguration.class))
        .withPropertyValues(
            "krizaka.messaging.dedup.store=memory", "krizaka.messaging.dedup.retention=3d")
        .run(
            context -> {
              assertThat(context.getBean(MessageDedupPurgeScheduler.class).isRunning()).isTrue();
              assertThat(context.getBean(MessageDedupProperties.class).retention())
                  .isEqualTo(Duration.ofDays(3));
            });
  }
}
