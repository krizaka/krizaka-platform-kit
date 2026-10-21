package com.krizaka.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import org.junit.jupiter.api.Test;

class SchemaMigrationTest {

  private static final ClassLoader LOADER = SchemaMigrationTest.class.getClassLoader();

  private static SchemaSet outbox(String table) {
    return new SchemaSet(
        "outbox",
        "1.1",
        "com/krizaka/messaging/outbox/schema-postgresql.sql",
        "krizaka_outbox",
        table);
  }

  @Test
  void runsTheModulesOwnSchemaStatementByStatement() {
    SchemaMigration migration = new SchemaMigration(outbox(null), LOADER);

    assertThat(migration.statements())
        .hasSize(3)
        .allSatisfy(s -> assertThat(s).doesNotStartWith("--"));
    assertThat(migration.statements().getFirst())
        .startsWith("CREATE TABLE IF NOT EXISTS krizaka_outbox (");
    assertThat(migration.getVersion().getVersion()).isEqualTo("1.1");
    assertThat(migration.getDescription()).isEqualTo("krizaka outbox");
  }

  @Test
  void aConfiguredTableRenamesTheTableAndItsIndexes() {
    SchemaMigration migration = new SchemaMigration(outbox("billing.wallet_outbox"), LOADER);

    assertThat(String.join(";", migration.statements()))
        .contains("CREATE TABLE IF NOT EXISTS billing.wallet_outbox (")
        .contains("INDEX IF NOT EXISTS wallet_outbox_pending ON billing.wallet_outbox")
        .doesNotContain("krizaka_outbox");
  }

  @Test
  void theChecksumIsTheSqlItRuns() {
    assertThat(new SchemaMigration(outbox(null), LOADER).getChecksum())
        .isEqualTo(new SchemaMigration(outbox("krizaka_outbox"), LOADER).getChecksum())
        .isNotEqualTo(new SchemaMigration(outbox("wallet_outbox"), LOADER).getChecksum());
  }

  @Test
  void refusesATableNameThatIsNotOne() {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new SchemaMigration(outbox("x; DROP TABLE y"), LOADER));
  }
}
