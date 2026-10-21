package com.krizaka.messaging.dedup;

import org.springframework.boot.context.properties.ConfigurationProperties;
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
 */
@ConfigurationProperties(prefix = "krizaka.messaging.dedup")
public record MessageDedupProperties(
    Store store, @DefaultValue(JdbcMessageDedup.DEFAULT_TABLE) String table) {

  /** Normalises the defaults. */
  public MessageDedupProperties {
    table = table == null || table.isBlank() ? JdbcMessageDedup.DEFAULT_TABLE : table;
  }

  /** The two claim stores. */
  public enum Store {
    /** {@link JdbcMessageDedup}. */
    JDBC,
    /** {@link InMemoryMessageDedup}. */
    MEMORY
  }
}
