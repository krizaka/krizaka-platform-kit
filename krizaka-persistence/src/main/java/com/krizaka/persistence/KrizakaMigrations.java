package com.krizaka.persistence;

import java.util.List;
import java.util.Objects;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.migration.JavaMigration;
import org.flywaydb.core.api.output.MigrateResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;

/**
 * Creates and migrates the tables of the JDBC stores a service declared, at startup, before any of
 * them is used.
 *
 * <ul>
 *   <li>its own Flyway history table ({@value #DEFAULT_HISTORY_TABLE}): the kit's versions never
 *       meet the service's;
 *   <li>only the sets the service declared; a store added later is migrated out of order, at its
 *       own version;
 *   <li>a database that already has the tables (created by hand from the shipped schemas) is
 *       adopted: the schemas are {@code IF NOT EXISTS}, and the history is baselined.
 * </ul>
 */
public final class KrizakaMigrations implements InitializingBean {

  /** The default history table. */
  public static final String DEFAULT_HISTORY_TABLE = "krizaka_schema_history";

  // A location that holds nothing: the kit's migrations are Java ones, and Flyway's default
  // location (db/migration) belongs to the service.
  private static final String NO_LOCATION = "classpath:com/krizaka/persistence/no-sql-migrations";

  private static final Logger log = LoggerFactory.getLogger(KrizakaMigrations.class);

  private final DataSource dataSource;
  private final List<JavaMigration> migrations;
  private final String historyTable;

  /**
   * The migrations of the declared sets.
   *
   * @param dataSource the service's database
   * @param sets the table sets the service declared, with its table names
   * @param historyTable the history table
   * @param classLoader where the modules' schemas are read
   */
  public KrizakaMigrations(
      DataSource dataSource, List<SchemaSet> sets, String historyTable, ClassLoader classLoader) {
    this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
    this.historyTable = Objects.requireNonNull(historyTable, "historyTable");
    this.migrations =
        sets.stream().<JavaMigration>map(s -> new SchemaMigration(s, classLoader)).toList();
  }

  /**
   * The sets that will be migrated.
   *
   * @return their descriptions, by version
   */
  public List<String> declared() {
    return migrations.stream().map(JavaMigration::getDescription).toList();
  }

  /**
   * Migrates now.
   *
   * @return how many migrations ran
   */
  public int migrate() {
    if (migrations.isEmpty()) {
      return 0;
    }
    MigrateResult result =
        Flyway.configure()
            .dataSource(dataSource)
            .table(historyTable)
            .locations(NO_LOCATION)
            .javaMigrations(migrations.toArray(JavaMigration[]::new))
            .baselineOnMigrate(true)
            .baselineVersion("0")
            .outOfOrder(true)
            .validateOnMigrate(true)
            .load()
            .migrate();
    if (result.migrationsExecuted > 0) {
      log.info("Krizaka migrations: {} applied ({})", result.migrationsExecuted, declared());
    }
    return result.migrationsExecuted;
  }

  @Override
  public void afterPropertiesSet() {
    migrate();
  }
}
