package com.krizaka.appconfig.store;

import com.krizaka.appconfig.model.AppConfig;
import com.krizaka.appconfig.model.ClientPlatform;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Reads the published parameters through a short cache, and never lets the store take the API down.
 *
 * <p>The filter reads this on every request, so the database is asked once per {@code ttl} per
 * platform. When the store fails, the last value read is served (stale beats locked out); when
 * there is none, the permissive {@linkplain AppConfig#defaults defaults} are: an outage of the
 * parameters must never turn into an outage of the app. A publication through {@link #publish},
 * {@link #rollback} evicts the entry, so this instance sees it at once.
 */
public final class AppConfigReader {

  private static final Logger LOG = LoggerFactory.getLogger(AppConfigReader.class);

  private record Entry(AppConfig config, Instant readAt) {}

  private final AppConfigStore store;
  private final Duration ttl;
  private final Clock clock;
  private final Map<ClientPlatform, Entry> cache = new ConcurrentHashMap<>();

  /**
   * Creates the reader.
   *
   * @param store where the parameters are
   * @param ttl how long a read value is reused
   * @param clock the clock
   */
  public AppConfigReader(AppConfigStore store, Duration ttl, Clock clock) {
    this.store = Objects.requireNonNull(store, "store");
    this.ttl = Objects.requireNonNull(ttl, "ttl");
    this.clock = Objects.requireNonNull(clock, "clock");
  }

  /**
   * The parameters of a platform.
   *
   * @param platform the platform
   * @return the published parameters, the last known ones when the store fails, else the defaults
   */
  public AppConfig get(ClientPlatform platform) {
    Instant now = clock.instant();
    Entry entry = cache.get(platform);
    if (entry != null && Duration.between(entry.readAt(), now).compareTo(ttl) < 0) {
      return entry.config();
    }
    try {
      AppConfig fresh = store.find(platform).orElseGet(() -> AppConfig.defaults(platform));
      cache.put(platform, new Entry(fresh, now));
      return fresh;
    } catch (RuntimeException failure) {
      LOG.warn("app-config store unavailable for {}: {}", platform.wire(), failure.toString());
      return entry != null ? entry.config() : AppConfig.defaults(platform);
    }
  }

  /**
   * Publishes and makes the new value visible at once.
   *
   * @param config the new parameters
   * @param actor who
   * @param reason why
   * @return the published revision
   */
  public AppConfig publish(AppConfig config, String actor, String reason) {
    AppConfig published = store.publish(config, actor, reason);
    cache.remove(config.platform());
    return published;
  }

  /**
   * Restores an earlier revision and makes it visible at once.
   *
   * @param platform the platform
   * @param revision the revision to restore
   * @param actor who
   * @param reason why
   * @return the new revision, or empty when there is no such revision
   */
  public java.util.Optional<AppConfig> rollback(
      ClientPlatform platform, int revision, String actor, String reason) {
    java.util.Optional<AppConfig> restored = store.rollback(platform, revision, actor, reason);
    cache.remove(platform);
    return restored;
  }

  /**
   * The store, for the admin API's reads that must not be cached.
   *
   * @return the store
   */
  public AppConfigStore store() {
    return store;
  }
}
