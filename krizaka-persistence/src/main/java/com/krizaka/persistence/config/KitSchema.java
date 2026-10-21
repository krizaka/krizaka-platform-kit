package com.krizaka.persistence.config;

import com.krizaka.persistence.SchemaSet;
import java.util.List;
import java.util.function.Predicate;
import org.springframework.core.env.Environment;
import org.springframework.core.io.ClassPathResource;

/**
 * One table set of the kit: the schema its module ships, the version it is migrated at, the
 * declaration that asks for it and the property that renames its table.
 *
 * @param id a short name, the migration's description
 * @param version the Flyway version, unique across the kit
 * @param resource the classpath schema the module ships
 * @param defaultTable the table name the schema uses; renamed to the property's value, indexes
 *     included
 * @param tableProperty the property naming the table, or {@code null} when it is fixed
 * @param declared whether the service declared this store
 */
public record KitSchema(
    String id,
    String version,
    String resource,
    String defaultTable,
    String tableProperty,
    Predicate<Environment> declared) {

  /** Every table set of the kit, by version. */
  public static final List<KitSchema> ALL =
      List.of(
          new KitSchema(
              "outbox",
              "1.1",
              "com/krizaka/messaging/outbox/schema-postgresql.sql",
              "krizaka_outbox",
              "krizaka.messaging.outbox.jdbc.table",
              properties ->
                  properties.getProperty(
                      "krizaka.messaging.outbox.jdbc.enabled", Boolean.class, false)),
          new KitSchema(
              "message-claims",
              "1.2",
              "com/krizaka/messaging/dedup/schema-postgresql.sql",
              "processed_messages",
              "krizaka.messaging.dedup.table",
              properties ->
                  "jdbc".equalsIgnoreCase(properties.getProperty("krizaka.messaging.dedup.store"))),
          new KitSchema(
              "app-config",
              "2.1",
              "krizaka-app-config-schema.sql",
              "app_config",
              null,
              properties ->
                  properties.getProperty("krizaka.app-config.enabled", Boolean.class, true)
                      && !"memory"
                          .equalsIgnoreCase(properties.getProperty("krizaka.app-config.store"))),
          new KitSchema(
              "idempotency-keys",
              "3.1",
              "com/krizaka/idempotency/schema-postgresql.sql",
              "idempotency_keys",
              "krizaka.idempotency.table",
              properties ->
                  "jdbc".equalsIgnoreCase(properties.getProperty("krizaka.idempotency.store"))));

  /**
   * The sets this service needs: its module is on the classpath and its JDBC store is declared.
   *
   * @param properties the service's properties
   * @param classLoader where the modules' schemas are looked up
   * @return the sets to migrate, by version, with the service's table names
   */
  public static List<SchemaSet> declared(Environment properties, ClassLoader classLoader) {
    return ALL.stream()
        .filter(s -> new ClassPathResource(s.resource(), classLoader).exists())
        .filter(s -> s.declared().test(properties))
        .map(
            s ->
                new SchemaSet(
                    s.id(), s.version(), s.resource(), s.defaultTable(), s.table(properties)))
        .toList();
  }

  /**
   * The table this service uses.
   *
   * @param properties the service's properties
   * @return the configured name, or the default
   */
  public String table(Environment properties) {
    return tableProperty == null
        ? defaultTable
        : properties.getProperty(tableProperty, defaultTable);
  }
}
