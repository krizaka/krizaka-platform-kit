package com.krizaka.messaging.dedup;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Where claims are kept ({@code krizaka.messaging.dedup}).
 *
 * <p>The store is declared, never inferred from what happens to be on the classpath: a service that
 * silently fell back to memory because its {@code JdbcTemplate} was missing would lose the
 * guarantee it believes it has.
 *
 * @param store Where claims are kept: 'jdbc' (durable, shared by the instances) or 'memory' (one
 *     instance). Required to get a MessageDedup bean; never inferred from the classpath.
 * @param table Table the claims are kept in when the store is 'jdbc'.
 * @param retention How long a claim is kept; beyond the broker's longest redelivery window, a claim
 *     protects nothing.
 * @param purgeInterval Delay between two purges of the claims older than the retention.
 */
@ConfigurationProperties(prefix = "krizaka.messaging.dedup")
public record MessageDedupProperties(
    Store store,
    @DefaultValue(JdbcMessageDedup.DEFAULT_TABLE) String table,
    @DefaultValue("7d") Duration retention,
    @DefaultValue("1h") Duration purgeInterval) {

  /** Normalises the defaults. */
  @ConstructorBinding
  public MessageDedupProperties {
    table = table == null || table.isBlank() ? JdbcMessageDedup.DEFAULT_TABLE : table;
    retention = retention == null ? Duration.ofDays(7) : retention;
    purgeInterval = purgeInterval == null ? Duration.ofHours(1) : purgeInterval;
    if (!positive(retention) || !positive(purgeInterval)) {
      throw new IllegalArgumentException(
          "krizaka.messaging.dedup.retention and purge-interval must be positive");
    }
  }

  /**
   * The properties of 0.2: a store and a table, the default retention and purge interval.
   *
   * @param store where claims are kept
   * @param table the claims table
   */
  public MessageDedupProperties(Store store, String table) {
    this(store, table, null, null);
  }

  private static boolean positive(Duration duration) {
    return !duration.isNegative() && !duration.isZero();
  }

  /** The two claim stores. */
  public enum Store {
    /** {@link JdbcMessageDedup}. */
    JDBC,
    /** {@link InMemoryMessageDedup}. */
    MEMORY
  }
}
