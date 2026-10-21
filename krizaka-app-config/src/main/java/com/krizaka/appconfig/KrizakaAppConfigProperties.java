package com.krizaka.appconfig;

import com.krizaka.appconfig.web.AppConfigPaths;
import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * How a service wires app-config ({@code krizaka.app-config}). The <i>values</i> that decide what
 * an app does (minimum version, maintenance…) are database parameters, not properties; these are
 * only the wiring that must be known before the database can be read.
 *
 * @param enabled Whether the app-config endpoint and the version filter are registered.
 * @param store Where the parameters are kept: 'jdbc' (the application's database) or 'memory'
 *     (tests and demos).
 * @param cacheTtl How long this instance reuses a parameter set it read.
 * @param httpMaxAge Max-age of the public app-config endpoint, for CDNs and clients.
 * @param guard Which requests the 426 Upgrade Required filter looks at.
 * @param admin The admin API.
 */
@ConfigurationProperties(prefix = "krizaka.app-config")
public record KrizakaAppConfigProperties(
    @DefaultValue("true") Boolean enabled,
    @DefaultValue("jdbc") Store store,
    @DefaultValue("30s") Duration cacheTtl,
    @DefaultValue("60s") Duration httpMaxAge,
    @DefaultValue Guard guard,
    @DefaultValue Admin admin) {

  /** Where the parameters live. */
  public enum Store {
    /** PostgreSQL through the application's {@code JdbcTemplate} (the default). */
    JDBC,
    /** Memory: tests and demos. */
    MEMORY
  }

  /** Applies the defaults. */
  public KrizakaAppConfigProperties {
    enabled = enabled == null || enabled;
    store = store == null ? Store.JDBC : store;
    cacheTtl = positive(cacheTtl, Duration.ofSeconds(30), "cache-ttl");
    httpMaxAge = positive(httpMaxAge, Duration.ofSeconds(60), "http-max-age");
    guard = guard == null ? new Guard(null, null, null) : guard;
    admin = admin == null ? new Admin(null, null) : admin;
  }

  private static Duration positive(Duration value, Duration fallback, String name) {
    Duration chosen = value == null ? fallback : value;
    if (chosen.isNegative()) {
      throw new IllegalArgumentException("krizaka.app-config." + name + " cannot be negative");
    }
    return chosen;
  }

  /**
   * The {@code 426} filter ({@code krizaka.app-config.guard}).
   *
   * @param enabled Whether the 426 Upgrade Required filter is registered.
   * @param include Ant patterns of the paths the filter guards.
   * @param exclude Ant patterns the filter never guards: the app-config endpoint itself, so an old
   *     client can always learn it must update.
   */
  public record Guard(
      @DefaultValue("true") Boolean enabled,
      @DefaultValue("/api/**") List<String> include,
      @DefaultValue({AppConfigPaths.ENDPOINT, AppConfigPaths.ADMIN + "/**"}) List<String> exclude) {

    /** The public endpoint's path. */
    public static final String ENDPOINT = AppConfigPaths.ENDPOINT;

    /** The admin API's base path. */
    public static final String ADMIN = AppConfigPaths.ADMIN;

    /** Applies the defaults. */
    public Guard {
      enabled = enabled == null || enabled;
      include = include == null || include.isEmpty() ? List.of("/api/**") : List.copyOf(include);
      exclude =
          exclude == null || exclude.isEmpty()
              ? List.of(ENDPOINT, ADMIN + "/**")
              : List.copyOf(exclude);
    }
  }

  /**
   * The admin API ({@code krizaka.app-config.admin}).
   *
   * @param enabled Whether the audited admin API (/api/v1/admin/app-config) is registered.
   * @param authority Authority a caller must hold to use the admin API.
   */
  public record Admin(
      @DefaultValue("false") Boolean enabled, @DefaultValue("ROLE_ADMIN") String authority) {

    /** Applies the defaults. */
    public Admin {
      enabled = enabled != null && enabled;
      authority = authority == null || authority.isBlank() ? "ROLE_ADMIN" : authority;
    }
  }
}
