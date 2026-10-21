package com.krizaka.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.krizaka.test.container.AbstractContainerIntegrationTest;
import java.util.List;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

/** The kit's migrations on a real PostgreSQL, each test in a fresh schema. */
class KrizakaMigrationsIT extends AbstractContainerIntegrationTest {

  private static final ClassLoader LOADER = KrizakaMigrationsIT.class.getClassLoader();

  private static final SchemaSet OUTBOX =
      new SchemaSet(
          "outbox",
          "1.1",
          "com/krizaka/messaging/outbox/schema-postgresql.sql",
          "krizaka_outbox",
          null);
  private static final SchemaSet CLAIMS =
      new SchemaSet(
          "message-claims",
          "1.2",
          "com/krizaka/messaging/dedup/schema-postgresql.sql",
          "processed_messages",
          null);
  private static final SchemaSet APP_CONFIG =
      new SchemaSet("app-config", "2.1", "krizaka-app-config-schema.sql", "app_config", null);
  private static final SchemaSet KEYS =
      new SchemaSet(
          "idempotency-keys",
          "3.1",
          "com/krizaka/idempotency/schema-postgresql.sql",
          "idempotency_keys",
          null);

  private String schema;
  private DataSource dataSource;
  private JdbcTemplate jdbc;

  @BeforeEach
  void freshSchema() {
    schema = "s_" + UUID.randomUUID().toString().replace("-", "");
    JdbcTemplate admin =
        new JdbcTemplate(
            new DriverManagerDataSource(
                postgres().getJdbcUrl(), postgres().getUsername(), postgres().getPassword()));
    admin.execute("CREATE SCHEMA " + schema);
    String url = postgres().getJdbcUrl();
    dataSource =
        new DriverManagerDataSource(
            url + (url.contains("?") ? "&" : "?") + "currentSchema=" + schema,
            postgres().getUsername(),
            postgres().getPassword());
    jdbc = new JdbcTemplate(dataSource);
  }

  private List<String> tables() {
    return jdbc.queryForList(
        "SELECT table_name FROM information_schema.tables WHERE table_schema = ? ORDER BY 1",
        String.class,
        schema);
  }

  private KrizakaMigrations migrations(List<SchemaSet> sets) {
    return new KrizakaMigrations(dataSource, sets, KrizakaMigrations.DEFAULT_HISTORY_TABLE, LOADER);
  }

  @Test
  void createsEveryDeclaredSetInItsOwnHistoryTable() {
    assertThat(migrations(List.of(OUTBOX, CLAIMS, APP_CONFIG, KEYS)).migrate()).isEqualTo(4);

    assertThat(tables())
        .containsExactly(
            "app_config",
            "app_config_history",
            "idempotency_keys",
            "krizaka_outbox",
            "krizaka_schema_history",
            "processed_messages");
    assertThat(jdbc.queryForObject("SELECT count(*) FROM app_config", Integer.class))
        .as("the app-config seed ran")
        .isEqualTo(6);
  }

  @Test
  void runningAgainChangesNothing() {
    migrations(List.of(OUTBOX, CLAIMS)).migrate();

    assertThat(migrations(List.of(OUTBOX, CLAIMS)).migrate()).isZero();
  }

  @Test
  void onlyTheDeclaredSetsAndALaterOneIsAddedOutOfOrder() {
    migrations(List.of(KEYS)).migrate();
    assertThat(tables()).containsExactly("idempotency_keys", "krizaka_schema_history");

    assertThat(migrations(List.of(OUTBOX, KEYS)).migrate()).as("1.1 after 3.1").isEqualTo(1);
    assertThat(tables()).contains("krizaka_outbox");
  }

  @Test
  void neverTouchesTheServicesOwnMigrationsOrHistory() {
    jdbc.execute("CREATE TABLE flyway_schema_history (installed_rank INT)");
    jdbc.execute("CREATE TABLE orders (id INT)");

    assertThat(migrations(List.of(CLAIMS)).migrate()).isEqualTo(1);

    assertThat(jdbc.queryForObject("SELECT count(*) FROM flyway_schema_history", Integer.class))
        .isZero();
    assertThat(tables()).contains("orders", "processed_messages", "krizaka_schema_history");
  }

  @Test
  void adoptsTablesAnOperatorCreatedFromTheShippedSchemas() {
    jdbc.execute(
        "CREATE TABLE processed_messages (consumer VARCHAR(255) NOT NULL, message_id VARCHAR(255)"
            + " NOT NULL, processed_at TIMESTAMPTZ NOT NULL DEFAULT now(), PRIMARY KEY (consumer,"
            + " message_id))");
    jdbc.update("INSERT INTO processed_messages (consumer, message_id) VALUES ('c', 'm')");

    assertThat(migrations(List.of(CLAIMS)).migrate()).isEqualTo(1);

    assertThat(jdbc.queryForObject("SELECT count(*) FROM processed_messages", Integer.class))
        .as("the rows are kept")
        .isEqualTo(1);
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM pg_indexes WHERE schemaname = ? AND indexname ="
                    + " 'processed_messages_processed_at'",
                Integer.class,
                schema))
        .as("the index the purge needs is added")
        .isEqualTo(1);
  }

  @Test
  void aConfiguredTableNameIsCreatedAndRenamingItLaterFailsValidation() {
    SchemaSet wallet =
        new SchemaSet(
            "outbox",
            "1.1",
            "com/krizaka/messaging/outbox/schema-postgresql.sql",
            "krizaka_outbox",
            "wallet_outbox");
    migrations(List.of(wallet)).migrate();
    assertThat(tables()).contains("wallet_outbox").doesNotContain("krizaka_outbox");

    assertThatThrownBy(() -> migrations(List.of(OUTBOX)).migrate())
        .hasMessageContaining("checksum");
  }
}
