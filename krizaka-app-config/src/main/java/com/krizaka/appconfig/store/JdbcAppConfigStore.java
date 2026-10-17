package com.krizaka.appconfig.store;

import com.krizaka.appconfig.model.AppConfig;
import com.krizaka.appconfig.model.AppConfigChange;
import com.krizaka.appconfig.model.AppVersion;
import com.krizaka.appconfig.model.ClientPlatform;
import com.krizaka.appconfig.model.UpdateMode;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/**
 * The parameters in PostgreSQL: {@code app_config} (the published row of each platform) and {@code
 * app_config_history} (every revision, append-only). The tables are in the classpath resource
 * {@value #SCHEMA_RESOURCE}, which the service's own migration applies.
 *
 * <p>A publication is one statement (an upsert and the history line in the same CTE), so the row
 * and its trail cannot disagree, and two concurrent publications get distinct revisions.
 */
public final class JdbcAppConfigStore implements AppConfigStore {

  /** The classpath resource holding the tables and the version 1 seed. */
  public static final String SCHEMA_RESOURCE = "krizaka-app-config-schema.sql";

  private static final TypeReference<Map<String, String>> TEXTS = new TypeReference<>() {};
  private static final TypeReference<Map<String, Object>> FLAGS = new TypeReference<>() {};

  private static final String COLUMNS =
      "platform, min_supported_version, latest_version, update_override, store_url, maintenance_on,"
          + " maintenance_message::text AS maintenance_message, message::text AS message, contract_min,"
          + " contract_max, contract_sunset, flags::text AS flags, revision";

  private final JdbcTemplate jdbc;
  private final JsonMapper json;
  private final RowMapper<AppConfig> currentMapper = this::mapCurrent;

  /**
   * Creates the store.
   *
   * @param jdbc the database holding the tables
   * @param json the mapper for the JSON columns
   */
  public JdbcAppConfigStore(JdbcTemplate jdbc, JsonMapper json) {
    this.jdbc = Objects.requireNonNull(jdbc, "jdbc");
    this.json = Objects.requireNonNull(json, "json");
  }

  @Override
  public Optional<AppConfig> find(ClientPlatform platform) {
    return jdbc
        .query(
            "SELECT " + COLUMNS + ", updated_at, updated_by FROM app_config WHERE platform = ?",
            currentMapper,
            platform.wire())
        .stream()
        .findFirst();
  }

  @Override
  public List<AppConfig> findAll() {
    return jdbc.query(
        "SELECT " + COLUMNS + ", updated_at, updated_by FROM app_config ORDER BY platform",
        currentMapper);
  }

  @Override
  public AppConfig publish(AppConfig config, String actor, String reason) {
    InMemoryAppConfigStore.requireReason(reason);
    Objects.requireNonNull(actor, "actor");
    Integer revision =
        jdbc.queryForObject(
            """
            WITH upsert AS (
              INSERT INTO app_config (platform, min_supported_version, latest_version, update_override,
                  store_url, maintenance_on, maintenance_message, message, contract_min, contract_max,
                  contract_sunset, flags, revision, updated_at, updated_by)
              VALUES (?, ?, ?, ?, ?, ?, ?::jsonb, ?::jsonb, ?, ?, ?, ?::jsonb, 1, now(), ?)
              ON CONFLICT (platform) DO UPDATE SET
                  min_supported_version = EXCLUDED.min_supported_version,
                  latest_version = EXCLUDED.latest_version,
                  update_override = EXCLUDED.update_override,
                  store_url = EXCLUDED.store_url,
                  maintenance_on = EXCLUDED.maintenance_on,
                  maintenance_message = EXCLUDED.maintenance_message,
                  message = EXCLUDED.message,
                  contract_min = EXCLUDED.contract_min,
                  contract_max = EXCLUDED.contract_max,
                  contract_sunset = EXCLUDED.contract_sunset,
                  flags = EXCLUDED.flags,
                  revision = app_config.revision + 1,
                  updated_at = now(),
                  updated_by = EXCLUDED.updated_by
              RETURNING *),
            trail AS (
              INSERT INTO app_config_history (platform, revision, min_supported_version, latest_version,
                  update_override, store_url, maintenance_on, maintenance_message, message, contract_min,
                  contract_max, contract_sunset, flags, reason, changed_by, changed_at)
              SELECT platform, revision, min_supported_version, latest_version, update_override, store_url,
                  maintenance_on, maintenance_message, message, contract_min, contract_max, contract_sunset,
                  flags, ?, updated_by, updated_at
              FROM upsert)
            SELECT revision FROM upsert
            """,
            Integer.class,
            config.platform().wire(),
            config.minSupportedVersion().toString(),
            config.latestVersion().toString(),
            config.updateOverride() == null ? null : config.updateOverride().wire(),
            config.storeUrl(),
            config.maintenanceOn(),
            write(config.maintenanceMessage()),
            write(config.message()),
            config.contractMin(),
            config.contractMax(),
            config.contractSunset() == null ? null : java.sql.Date.valueOf(config.contractSunset()),
            write(config.flags()),
            actor,
            reason);
    return find(config.platform())
        .filter(row -> Objects.equals(row.revision(), revision))
        .orElseThrow();
  }

  @Override
  public List<AppConfigChange> history(ClientPlatform platform) {
    return jdbc.query(
        "SELECT "
            + COLUMNS
            + ", changed_at AS updated_at, changed_by AS updated_by, reason, changed_by, changed_at"
            + " FROM app_config_history WHERE platform = ? ORDER BY revision DESC",
        (rs, row) ->
            new AppConfigChange(
                mapCurrent(rs, row),
                rs.getString("reason"),
                rs.getString("changed_by"),
                rs.getTimestamp("changed_at").toInstant()),
        platform.wire());
  }

  @Override
  public Optional<AppConfig> rollback(
      ClientPlatform platform, int revision, String actor, String reason) {
    InMemoryAppConfigStore.requireReason(reason);
    return history(platform).stream()
        .filter(change -> change.config().revision() == revision)
        .findFirst()
        .map(change -> publish(change.config(), actor, reason));
  }

  private AppConfig mapCurrent(ResultSet rs, int row) throws SQLException {
    Timestamp updatedAt = rs.getTimestamp("updated_at");
    java.sql.Date sunset = rs.getDate("contract_sunset");
    LocalDate sunsetDate = sunset == null ? null : sunset.toLocalDate();
    return new AppConfig(
        ClientPlatform.parse(rs.getString("platform")).orElseThrow(),
        AppVersion.of(rs.getString("min_supported_version")),
        AppVersion.of(rs.getString("latest_version")),
        UpdateMode.parse(rs.getString("update_override")).orElse(null),
        rs.getString("store_url"),
        rs.getBoolean("maintenance_on"),
        json.readValue(rs.getString("maintenance_message"), TEXTS),
        json.readValue(rs.getString("message"), TEXTS),
        rs.getInt("contract_min"),
        rs.getInt("contract_max"),
        sunsetDate,
        json.readValue(rs.getString("flags"), FLAGS),
        rs.getInt("revision"),
        updatedAt.toInstant(),
        rs.getString("updated_by"));
  }

  private String write(Object value) {
    return json.writeValueAsString(value);
  }
}
