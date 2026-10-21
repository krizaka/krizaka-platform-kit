package com.krizaka.messaging.outbox;

import static org.assertj.core.api.Assertions.assertThat;

import com.krizaka.test.container.AbstractContainerIntegrationTest;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import tools.jackson.databind.json.JsonMapper;

/** The shipped outbox store on a real PostgreSQL, with the schema the module ships. */
class JdbcOutboxStoreIT extends AbstractContainerIntegrationTest {

  private static JdbcTemplate jdbc;
  private static JdbcOutboxStore store;

  /** Its own table: the relay of another test's Spring context must not lease these rows. */
  private static final String TABLE = "outbox_store_it";

  @BeforeAll
  static void schema() throws Exception {
    jdbc =
        new JdbcTemplate(
            new DriverManagerDataSource(
                postgres().getJdbcUrl(), postgres().getUsername(), postgres().getPassword()));
    String schema =
        new ClassPathResource("com/krizaka/messaging/outbox/schema-postgresql.sql")
            .getContentAsString(StandardCharsets.UTF_8)
            .replace("krizaka_outbox", TABLE);
    for (String statement : schema.replaceAll("(?m)^--.*$", "").split(";")) {
      if (!statement.isBlank()) {
        jdbc.execute(statement);
      }
    }
    store = new JdbcOutboxStore(jdbc, JsonMapper.shared(), TABLE, Duration.ofMinutes(15));
  }

  @BeforeEach
  void empty() {
    jdbc.update("DELETE FROM " + TABLE);
  }

  private static void append(String messageId) {
    store.append(
        new NewOutboxMessage(
            "platform.events",
            "evt.wallet.debited",
            messageId,
            "{\"credits\":5}".getBytes(StandardCharsets.UTF_8),
            Map.of("kz-type", "evt.wallet.debited")));
  }

  @Test
  void anAppendedRowIsLeasedWithItsBodyAndHeaders() {
    append("m-1");

    List<OutboxMessage> leased = store.leaseBatch(10, Duration.ofMinutes(2));

    assertThat(leased).hasSize(1);
    OutboxMessage row = leased.getFirst();
    assertThat(row.messageId()).isEqualTo("m-1");
    assertThat(row.routingKey()).isEqualTo("evt.wallet.debited");
    assertThat(new String(row.body(), StandardCharsets.UTF_8)).isEqualTo("{\"credits\": 5}");
    assertThat(row.headers()).containsEntry("kz-type", "evt.wallet.debited");
  }

  @Test
  void aLeasedRowIsInvisibleToAnotherRelayUntilTheLeaseEnds() {
    append("m-2");

    assertThat(store.leaseBatch(10, Duration.ofMinutes(2))).hasSize(1);
    assertThat(store.leaseBatch(10, Duration.ofMinutes(2))).as("a second relay").isEmpty();

    jdbc.update("UPDATE " + TABLE + " SET next_attempt_at = now() - interval '1 second'");
    assertThat(store.leaseBatch(10, Duration.ofMinutes(2))).as("the lease ended").hasSize(1);
  }

  @Test
  void aPublishedRowIsNeverLeasedAgainAndIsPurgedAfterTheRetention() {
    append("m-3");
    UUID id = store.leaseBatch(10, Duration.ofMillis(1)).getFirst().id();

    store.markPublished(id);
    jdbc.update("UPDATE " + TABLE + " SET next_attempt_at = now() - interval '1 second'");

    assertThat(store.leaseBatch(10, Duration.ofMinutes(2))).isEmpty();
    assertThat(store.purgePublishedBefore(Instant.now().minusSeconds(3600))).isZero();
    assertThat(store.purgePublishedBefore(Instant.now().plusSeconds(60))).isEqualTo(1);
  }

  @Test
  void aFailureBacksTheRowOffAndCountsTheAttempt() {
    append("m-4");
    UUID id = store.leaseBatch(10, Duration.ofMillis(1)).getFirst().id();

    store.recordFailure(id, 3);

    Map<String, Object> row =
        jdbc.queryForMap(
            "SELECT attempts, EXTRACT(EPOCH FROM next_attempt_at - now()) AS wait FROM " + TABLE);
    assertThat(row.get("attempts")).isEqualTo(1);
    assertThat(((Number) row.get("wait")).doubleValue()).isBetween(6.0, 8.5);
  }

  @Test
  void theOldestRowsComeFirstAndTheBatchIsBounded() {
    for (int i = 0; i < 5; i++) {
      append("m-batch-" + i);
    }

    assertThat(store.leaseBatch(3, Duration.ofMinutes(2)))
        .extracting(OutboxMessage::messageId)
        .containsExactly("m-batch-0", "m-batch-1", "m-batch-2");
  }
}
