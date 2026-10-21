package com.krizaka.messaging.outbox;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * The kit's own outbox store ({@code krizaka.messaging.outbox.jdbc}).
 *
 * @param enabled Whether the kit contributes JdbcOutboxStore, its PostgreSQL outbox, instead of a
 *     store the application writes. Off by default: a service that has its own keeps it.
 * @param table Table of the outbox (schema com/krizaka/messaging/outbox/schema-postgresql.sql).
 * @param maxBackoff Longest wait before a row whose publish failed is tried again; the waits double
 *     from one second up to it.
 */
@ConfigurationProperties(prefix = "krizaka.messaging.outbox.jdbc")
public record JdbcOutboxProperties(
    @DefaultValue("false") boolean enabled,
    @DefaultValue(JdbcOutboxStore.DEFAULT_TABLE) String table,
    @DefaultValue("15m") Duration maxBackoff) {

  /** Applies the defaults. */
  public JdbcOutboxProperties {
    table = table == null || table.isBlank() ? JdbcOutboxStore.DEFAULT_TABLE : table;
    maxBackoff = maxBackoff == null ? Duration.ofMinutes(15) : maxBackoff;
  }
}
