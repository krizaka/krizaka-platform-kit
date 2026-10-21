package com.krizaka.persistence;

import java.util.Objects;

/**
 * One table set to migrate: the schema a module ships, at its version, with the table name the
 * service uses.
 *
 * @param id a short name, the migration's description
 * @param version the Flyway version, unique across the kit
 * @param resource the classpath schema the module ships
 * @param defaultTable the table name the schema uses
 * @param table the name the service uses; the default name and every name built on it (indexes) are
 *     renamed to it
 */
public record SchemaSet(
    String id, String version, String resource, String defaultTable, String table) {

  /** Refuses an incomplete set. */
  public SchemaSet {
    Objects.requireNonNull(id, "id");
    Objects.requireNonNull(version, "version");
    Objects.requireNonNull(resource, "resource");
    Objects.requireNonNull(defaultTable, "defaultTable");
    table = table == null || table.isBlank() ? defaultTable : table;
  }
}
