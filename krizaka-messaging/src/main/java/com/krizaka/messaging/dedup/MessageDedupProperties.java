package com.krizaka.messaging.dedup;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Where claims are kept ({@code krizaka.messaging.dedup}).
 *
 * <p>The store is declared, never inferred from what happens to be on the classpath: a service that
 * silently fell back to memory because its {@code JdbcTemplate} was missing would lose the
 * guarantee it believes it has.
 *
 * @param store {@code jdbc} (durable, shared by instances) or {@code memory} (per instance)
 * @param table the claims table when {@code store=jdbc}; defaults to {@value
 *     JdbcMessageDedup#DEFAULT_TABLE}
 */
@ConfigurationProperties(prefix = "krizaka.messaging.dedup")
public record MessageDedupProperties(Store store, String table) {

  /** Normalises the defaults. */
  public MessageDedupProperties {
    table = (table == null || table.isBlank()) ? JdbcMessageDedup.DEFAULT_TABLE : table;
  }

  /** The two claim stores. */
  public enum Store {
    /** {@link JdbcMessageDedup}. */
    JDBC,
    /** {@link InMemoryMessageDedup}. */
    MEMORY
  }
}
