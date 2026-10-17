package com.krizaka.appconfig;

import com.krizaka.appconfig.web.AppConfigPaths;
import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * How a service wires app-config ({@code krizaka.app-config}). The <i>values</i> that decide what
 * an app does (minimum version, maintenance…) are database parameters, not properties; these are
 * only the wiring that must be known before the database can be read.
 *
 * @param enabled whether the endpoint and the filter are registered
 * @param store where the parameters are kept
 * @param cacheTtl how long a read parameter set is reused by this instance
 * @param httpMaxAge the {@code max-age} of the public endpoint, for CDNs and clients
 * @param guard which requests the {@code 426} filter looks at
 * @param admin the admin API
 */
@ConfigurationProperties(prefix = "krizaka.app-config")
public record KrizakaAppConfigProperties(
    Boolean enabled,
    Store store,
    Duration cacheTtl,
    Duration httpMaxAge,
    Guard guard,
    Admin admin) {

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
   * @param enabled whether the filter is registered
   * @param include ant patterns of the guarded paths; defaults to {@code /api/**}
   * @param exclude ant patterns never guarded (the app-config endpoint itself, so an old client can
   *     always learn it must update)
   */
  public record Guard(Boolean enabled, List<String> include, List<String> exclude) {

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
   * @param enabled whether {@code /api/v1/admin/app-config} is registered; off by default
   * @param authority the authority an admin must hold; defaults to {@code ROLE_ADMIN}
   */
  public record Admin(Boolean enabled, String authority) {

    /** Applies the defaults. */
    public Admin {
      enabled = enabled != null && enabled;
      authority = authority == null || authority.isBlank() ? "ROLE_ADMIN" : authority;
    }
  }
}
