package com.krizaka.persistence.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.krizaka.persistence.KrizakaMigrations;
import com.krizaka.test.container.AbstractContainerIntegrationTest;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

/** The starter in a context: the declared stores' tables exist once the context has started. */
class KrizakaPersistenceAutoConfigurationIT extends AbstractContainerIntegrationTest {

  private static DataSource freshSchema(String schema) {
    new JdbcTemplate(
            new DriverManagerDataSource(
                postgres().getJdbcUrl(), postgres().getUsername(), postgres().getPassword()))
        .execute("CREATE SCHEMA " + schema);
    String url = postgres().getJdbcUrl();
    return new DriverManagerDataSource(
        url + (url.contains("?") ? "&" : "?") + "currentSchema=" + schema,
        postgres().getUsername(),
        postgres().getPassword());
  }

  private final ApplicationContextRunner runner =
      new ApplicationContextRunner()
          .withConfiguration(AutoConfigurations.of(KrizakaPersistenceAutoConfiguration.class));

  @Test
  void theDeclaredStoresTablesExistOnceTheContextHasStarted() {
    String schema = "s_" + UUID.randomUUID().toString().replace("-", "");
    DataSource dataSource = freshSchema(schema);
    runner
        .withBean(DataSource.class, () -> dataSource)
        .withPropertyValues(
            "krizaka.messaging.outbox.jdbc.enabled=true",
            "krizaka.messaging.dedup.store=jdbc",
            "krizaka.idempotency.store=jdbc",
            "krizaka.app-config.enabled=false")
        .run(
            context -> {
              assertThat(context.getBean(KrizakaMigrations.class).declared())
                  .containsExactly(
                      "krizaka outbox", "krizaka message-claims", "krizaka idempotency-keys");
              assertThat(
                      new JdbcTemplate(dataSource)
                          .queryForList(
                              "SELECT table_name FROM information_schema.tables WHERE"
                                  + " table_schema = ? ORDER BY 1",
                              String.class,
                              schema))
                  .containsExactly(
                      "idempotency_keys",
                      "krizaka_outbox",
                      "krizaka_schema_history",
                      "processed_messages");
            });
  }

  @Test
  void turnedOffOrWithoutADatabaseItDoesNothing() {
    runner
        .withBean(
            DataSource.class, () -> new DriverManagerDataSource("jdbc:postgresql://nowhere/x"))
        .withPropertyValues("krizaka.persistence.migrations.enabled=false")
        .run(context -> assertThat(context).doesNotHaveBean(KrizakaMigrations.class));
    runner.run(context -> assertThat(context).doesNotHaveBean(KrizakaMigrations.class));
  }
}
