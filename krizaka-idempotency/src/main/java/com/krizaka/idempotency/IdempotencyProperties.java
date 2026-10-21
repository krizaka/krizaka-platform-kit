package com.krizaka.idempotency;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * How keys are kept ({@code krizaka.idempotency}).
 *
 * @param store Where keys and their responses are kept: 'jdbc' (PostgreSQL, in the action's
 *     transaction, shared by every instance) or 'memory' (one instance). Required to get the
 *     Idempotency bean; never inferred from the classpath.
 * @param table Table the keys are kept in when the store is 'jdbc'.
 * @param ttl How long a key replays its response; after it, the same key runs the action again.
 * @param purgeInterval Delay between two purges of the keys older than the time-to-live.
 */
@ConfigurationProperties(prefix = "krizaka.idempotency")
public record IdempotencyProperties(
    Store store,
    @DefaultValue(JdbcIdempotencyStore.DEFAULT_TABLE) String table,
    @DefaultValue("24h") Duration ttl,
    @DefaultValue("1h") Duration purgeInterval) {

  /** Applies the defaults and refuses a time-to-live or interval that could not work. */
  public IdempotencyProperties {
    table = table == null || table.isBlank() ? JdbcIdempotencyStore.DEFAULT_TABLE : table;
    ttl = ttl == null ? Duration.ofHours(24) : ttl;
    purgeInterval = purgeInterval == null ? Duration.ofHours(1) : purgeInterval;
    if (ttl.isNegative() || ttl.isZero()) {
      throw new IllegalArgumentException("krizaka.idempotency.ttl must be positive");
    }
    if (purgeInterval.isNegative() || purgeInterval.isZero()) {
      throw new IllegalArgumentException("krizaka.idempotency.purge-interval must be positive");
    }
  }

  /** The two stores. */
  public enum Store {
    /** {@link JdbcIdempotencyStore}. */
    JDBC,
    /** {@link InMemoryIdempotencyStore}. */
    MEMORY
  }
}
