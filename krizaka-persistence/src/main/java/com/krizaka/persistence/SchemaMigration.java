package com.krizaka.persistence;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.CRC32;
import org.flywaydb.core.api.MigrationVersion;
import org.flywaydb.core.api.migration.Context;
import org.flywaydb.core.api.migration.JavaMigration;
import org.springframework.core.io.ClassPathResource;

/**
 * A kit schema as a Flyway migration: the module's own {@code schema-postgresql.sql}, never a copy,
 * with its table renamed when the service configured another name. Its checksum is the SQL it runs,
 * so renaming a table after it was created fails Flyway's validation instead of silently creating a
 * second one.
 */
final class SchemaMigration implements JavaMigration {

  private static final Pattern IDENTIFIER =
      Pattern.compile("[A-Za-z_][A-Za-z0-9_]*(\\.[A-Za-z_][A-Za-z0-9_]*)?");

  private final SchemaSet schema;
  private final String sql;

  SchemaMigration(SchemaSet schema, ClassLoader classLoader) {
    this.schema = Objects.requireNonNull(schema, "schema");
    String table = schema.table();
    if (table == null || !IDENTIFIER.matcher(table).matches()) {
      throw new IllegalArgumentException("not a table name: " + table);
    }
    String source;
    try {
      source =
          new ClassPathResource(schema.resource(), classLoader)
              .getContentAsString(StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new UncheckedIOException("cannot read " + schema.resource(), e);
    }
    // The table takes the configured name; every name built on it (its indexes) takes the
    // configured name without its schema — an index lives in its table's schema and is never
    // qualified in CREATE INDEX.
    String simple = table.substring(table.lastIndexOf('.') + 1);
    String name = Pattern.quote(schema.defaultTable());
    this.sql =
        source
            .replaceAll("(?<![A-Za-z0-9_.])" + name + "(?=_)", Matcher.quoteReplacement(simple))
            .replaceAll(
                "(?<![A-Za-z0-9_.])" + name + "(?![A-Za-z0-9_])", Matcher.quoteReplacement(table));
  }

  /**
   * The statements, one per {@code ;}, comment lines removed.
   *
   * @return the statements in order
   */
  List<String> statements() {
    return Arrays.stream(sql.replaceAll("(?m)^\\s*--.*$", "").split(";"))
        .map(String::strip)
        .filter(s -> !s.isEmpty())
        .toList();
  }

  @Override
  public MigrationVersion getVersion() {
    return MigrationVersion.fromVersion(schema.version());
  }

  @Override
  public String getDescription() {
    return "krizaka " + schema.id();
  }

  @Override
  public Integer getChecksum() {
    CRC32 crc = new CRC32();
    crc.update(sql.getBytes(StandardCharsets.UTF_8));
    return (int) crc.getValue();
  }

  @Override
  public boolean canExecuteInTransaction() {
    return true;
  }

  @Override
  public void migrate(Context context) throws SQLException {
    Connection connection = context.getConnection();
    try (Statement statement = connection.createStatement()) {
      for (String each : statements()) {
        statement.execute(each);
      }
    }
  }
}
