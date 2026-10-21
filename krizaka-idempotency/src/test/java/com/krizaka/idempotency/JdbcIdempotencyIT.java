package com.krizaka.idempotency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.krizaka.test.container.AbstractContainerIntegrationTest;
import io.micrometer.observation.ObservationRegistry;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

/**
 * The JDBC store on a real PostgreSQL, with the schema the module ships: the claim waits on the
 * primary key, the response is written in the action's transaction, a rollback forgets the key.
 */
class JdbcIdempotencyIT extends AbstractContainerIntegrationTest {

  record Payment(String id, long cents) {}

  private static JdbcTemplate jdbc;
  private static Idempotency idempotency;

  @BeforeAll
  static void schema() throws Exception {
    DriverManagerDataSource dataSource =
        new DriverManagerDataSource(
            postgres().getJdbcUrl(), postgres().getUsername(), postgres().getPassword());
    jdbc = new JdbcTemplate(dataSource);
    String schema =
        new ClassPathResource("com/krizaka/idempotency/schema-postgresql.sql")
            .getContentAsString(StandardCharsets.UTF_8);
    for (String statement : schema.replaceAll("(?m)^--.*$", "").split(";")) {
      if (!statement.isBlank()) {
        jdbc.execute(statement);
      }
    }
    jdbc.execute("CREATE TABLE IF NOT EXISTS payments (id VARCHAR(64) PRIMARY KEY, cents BIGINT)");
    idempotency =
        new Idempotency(
            new JdbcIdempotencyStore(jdbc, JdbcIdempotencyStore.DEFAULT_TABLE),
            JsonMapper.shared(),
            new TransactionTemplate(new DataSourceTransactionManager(dataSource)),
            Duration.ofHours(24),
            Clock.systemUTC(),
            ObservationRegistry.NOOP);
  }

  private static Payment pay(long cents) {
    Payment payment = new Payment(UUID.randomUUID().toString(), cents);
    jdbc.update("INSERT INTO payments (id, cents) VALUES (?, ?)", payment.id(), payment.cents());
    return payment;
  }

  private static int payments(long cents) {
    return jdbc.queryForObject(
        "SELECT count(*) FROM payments WHERE cents = ?", Integer.class, cents);
  }

  private static IdempotentRequest request(String key, long cents) {
    return new IdempotentRequest(
        "payments.create", "user-1", key, java.util.Map.of("cents", cents));
  }

  @Test
  void aRetryReplaysAndPaysOnce() {
    String key = UUID.randomUUID().toString();

    IdempotentOutcome<Payment> first =
        idempotency.execute(request(key, 101), Payment.class, () -> pay(101));
    IdempotentOutcome<Payment> retry =
        idempotency.execute(request(key, 101), Payment.class, () -> pay(101));

    assertThat(retry.replayed()).isTrue();
    assertThat(retry.response()).isEqualTo(first.response());
    assertThat(payments(101)).isEqualTo(1);
  }

  @Test
  void theSameKeyForAnotherAmountIsRefused() {
    String key = UUID.randomUUID().toString();
    idempotency.execute(request(key, 102), Payment.class, () -> pay(102));

    assertThatThrownBy(() -> idempotency.execute(request(key, 999), Payment.class, () -> pay(999)))
        .isInstanceOf(IdempotencyKeyReusedException.class);
    assertThat(payments(999)).isZero();
  }

  @Test
  void aFailedActionIsRolledBackWithItsKeyAndTheRetryPays() {
    String key = UUID.randomUUID().toString();

    assertThatThrownBy(
            () ->
                idempotency.execute(
                    request(key, 103),
                    Payment.class,
                    () -> {
                      pay(103);
                      throw new IllegalStateException("provider refused");
                    }))
        .hasMessage("provider refused");
    assertThat(payments(103)).as("the action's own write is rolled back").isZero();

    assertThat(idempotency.execute(request(key, 103), Payment.class, () -> pay(103)).replayed())
        .isFalse();
    assertThat(payments(103)).isEqualTo(1);
  }

  @Test
  void twoConcurrentRequestsWithOneKeyPayOnce() throws Exception {
    String key = UUID.randomUUID().toString();
    CountDownLatch inside = new CountDownLatch(1);
    ExecutorService pool = Executors.newFixedThreadPool(2);
    try {
      Future<IdempotentOutcome<Payment>> first =
          pool.submit(
              () ->
                  idempotency.execute(
                      request(key, 104),
                      Payment.class,
                      () -> {
                        inside.countDown();
                        sleep(400);
                        return pay(104);
                      }));
      assertThat(inside.await(5, TimeUnit.SECONDS)).isTrue();
      Future<IdempotentOutcome<Payment>> second =
          pool.submit(() -> idempotency.execute(request(key, 104), Payment.class, () -> pay(104)));

      IdempotentOutcome<Payment> a = first.get(10, TimeUnit.SECONDS);
      IdempotentOutcome<Payment> b = second.get(10, TimeUnit.SECONDS);

      assertThat(a.replayed()).isFalse();
      assertThat(b.replayed()).as("the second waited on the key, then replayed").isTrue();
      assertThat(b.response()).isEqualTo(a.response());
      assertThat(payments(104)).isEqualTo(1);
    } finally {
      pool.shutdownNow();
    }
  }

  @Test
  void expiredKeysArePurged() {
    String key = UUID.randomUUID().toString();
    idempotency.execute(request(key, 105), Payment.class, () -> pay(105));
    JdbcIdempotencyStore store = new JdbcIdempotencyStore(jdbc, JdbcIdempotencyStore.DEFAULT_TABLE);

    long purged = store.purgeBefore(java.time.Instant.now().plusSeconds(60));

    assertThat(purged).isPositive();
    assertThat(idempotency.execute(request(key, 105), Payment.class, () -> pay(105)).replayed())
        .isFalse();
  }

  private static void sleep(long millis) {
    try {
      Thread.sleep(millis);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }
}
