package com.krizaka.persistence.config;

import com.krizaka.persistence.KrizakaMigrations;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * The kit's migrations ({@code krizaka.persistence}).
 *
 * @param migrations How the tables of the kit's JDBC stores are migrated.
 */
@ConfigurationProperties(prefix = "krizaka.persistence")
public record KrizakaPersistenceProperties(@DefaultValue Migrations migrations) {

  /** Applies the defaults. */
  public KrizakaPersistenceProperties {
    migrations = migrations == null ? new Migrations(null, null) : migrations;
  }

  /**
   * The kit's migrations ({@code krizaka.persistence.migrations}).
   *
   * @param enabled Whether the tables of the declared JDBC stores are created and migrated at
   *     startup. Turn it off when an operator runs the shipped schemas.
   * @param historyTable Flyway history table of the kit's migrations, kept apart from the service's
   *     own so their versions never meet.
   */
  public record Migrations(
      @DefaultValue("true") Boolean enabled,
      @DefaultValue(KrizakaMigrations.DEFAULT_HISTORY_TABLE) String historyTable) {

    /** Applies the defaults. */
    public Migrations {
      enabled = enabled == null || enabled;
      historyTable =
          historyTable == null || historyTable.isBlank()
              ? KrizakaMigrations.DEFAULT_HISTORY_TABLE
              : historyTable;
    }
  }
}
