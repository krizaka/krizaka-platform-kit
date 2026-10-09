package com.krizaka.messaging.dedup;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabase;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;

class JdbcMessageDedupTest {

  private EmbeddedDatabase database;
  private JdbcMessageDedup dedup;

  @BeforeEach
  void setUp() {
    database =
        new EmbeddedDatabaseBuilder()
            .setType(EmbeddedDatabaseType.H2)
            .generateUniqueName(true)
            .build();
    JdbcTemplate jdbc = new JdbcTemplate(database);
    jdbc.execute(
        "CREATE TABLE processed_messages (consumer VARCHAR(255) NOT NULL,"
            + " message_id VARCHAR(255) NOT NULL,"
            + " processed_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,"
            + " PRIMARY KEY (consumer, message_id))");
    dedup = new JdbcMessageDedup(jdbc);
  }

  @AfterEach
  void tearDown() {
    database.shutdown();
  }

  @Test
  void aMessageIsClaimedOnce() {
    assertThat(dedup.claim("billing", "m-1")).isTrue();
    assertThat(dedup.claim("billing", "m-1")).isFalse();
  }

  @Test
  void eachConsumerClaimsTheSameMessageOnce() {
    assertThat(dedup.claim("billing", "m-1")).isTrue();
    assertThat(dedup.claim("studio", "m-1")).isTrue();
  }

  @Test
  void aReleasedClaimCanBeClaimedAgain() {
    dedup.claim("billing", "m-1");
    dedup.release("billing", "m-1");
    assertThat(dedup.claim("billing", "m-1")).isTrue();
  }

  @Test
  void aMessageWithoutAnIdIsAlwaysClaimable() {
    assertThat(dedup.claim("billing", null)).isTrue();
    assertThat(dedup.claim("billing", "")).isTrue();
    assertThat(dedup.claim("billing", " ")).isTrue();
    dedup.release("billing", null);
  }

  @Test
  void concurrentDeliveriesOfOneMessageHaveExactlyOneWinner() throws Exception {
    int deliveries = 8;
    CountDownLatch start = new CountDownLatch(1);
    List<Callable<Boolean>> attempts = new ArrayList<>();
    for (int i = 0; i < deliveries; i++) {
      attempts.add(
          () -> {
            start.await();
            return dedup.claim("billing", "m-race");
          });
    }
    try (ExecutorService pool = Executors.newFixedThreadPool(deliveries)) {
      List<Future<Boolean>> results = attempts.stream().map(pool::submit).toList();
      start.countDown();
      long winners = 0;
      for (Future<Boolean> result : results) {
        winners += result.get() ? 1 : 0;
      }
      assertThat(winners).isEqualTo(1);
    }
  }

  @Test
  void housekeepingForgetsOldClaimsOnly() {
    JdbcTemplate jdbc = new JdbcTemplate(database);
    dedup.claim("billing", "old");
    dedup.claim("billing", "fresh");
    jdbc.update(
        "UPDATE processed_messages SET processed_at = DATEADD('DAY', -30, CURRENT_TIMESTAMP)"
            + " WHERE message_id = 'old'");

    assertThat(
            dedup.purgeClaimedBefore(java.time.Instant.now().minus(java.time.Duration.ofDays(7))))
        .isEqualTo(1);
    assertThat(dedup.claim("billing", "old")).as("forgotten, claimable again").isTrue();
    assertThat(dedup.claim("billing", "fresh")).as("still remembered").isFalse();
  }

  @Test
  void refusesATableNameThatIsNotAnIdentifier() {
    JdbcTemplate jdbc = new JdbcTemplate(database);
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new JdbcMessageDedup(jdbc, "claims; DROP TABLE users"));
    assertThat(new JdbcMessageDedup(jdbc, "billing.processed_messages")).isNotNull();
  }
}
