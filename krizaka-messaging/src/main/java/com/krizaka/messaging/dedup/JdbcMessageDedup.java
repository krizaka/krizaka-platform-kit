package com.krizaka.messaging.dedup;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Objects;
import java.util.regex.Pattern;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Durable claims: one row per {@code (consumer, message_id)}, arbitrated by a unique constraint.
 *
 * <p>The claim is a single {@code INSERT}; the database's unique constraint decides the race, so
 * two concurrent deliveries cannot both win. It survives restarts and is shared by every instance
 * of the service. The table it needs:
 *
 * <pre>{@code
 * CREATE TABLE processed_messages (
 *     consumer     VARCHAR(255) NOT NULL,
 *     message_id   VARCHAR(255) NOT NULL,
 *     processed_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
 *     PRIMARY KEY (consumer, message_id)
 * );
 * }</pre>
 *
 * <p>Call {@link #claim} inside the handler's transaction when the handler writes to the same
 * database: the claim then commits or rolls back with the work, and a failed handler releases its
 * claim by rolling back.
 */
public final class JdbcMessageDedup implements MessageDedup {

  /** The default table name. */
  public static final String DEFAULT_TABLE = "processed_messages";

  private static final Pattern IDENTIFIER =
      Pattern.compile("[A-Za-z_][A-Za-z0-9_]*(\\.[A-Za-z_][A-Za-z0-9_]*)?");

  private final JdbcTemplate jdbcTemplate;
  private final String insert;
  private final String delete;
  private final String purge;

  /**
   * Claims stored in {@value #DEFAULT_TABLE}.
   *
   * @param jdbcTemplate the database holding the claims table
   */
  public JdbcMessageDedup(JdbcTemplate jdbcTemplate) {
    this(jdbcTemplate, DEFAULT_TABLE);
  }

  /**
   * Claims stored in {@code table}.
   *
   * @param jdbcTemplate the database holding the claims table
   * @param table the table name, optionally schema-qualified ({@code schema.table}); it is
   *     validated as an identifier because it is part of the SQL text
   */
  public JdbcMessageDedup(JdbcTemplate jdbcTemplate, String table) {
    this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate");
    if (table == null || !IDENTIFIER.matcher(table).matches()) {
      throw new IllegalArgumentException("not a table name: " + table);
    }
    this.insert = "INSERT INTO " + table + " (consumer, message_id) VALUES (?, ?)";
    this.delete = "DELETE FROM " + table + " WHERE consumer = ? AND message_id = ?";
    this.purge = "DELETE FROM " + table + " WHERE processed_at < ?";
  }

  @Override
  public boolean claim(String consumer, String messageId) {
    if (messageId == null || messageId.isBlank()) {
      return true;
    }
    try {
      jdbcTemplate.update(insert, Objects.requireNonNull(consumer, "consumer"), messageId);
      return true;
    } catch (DuplicateKeyException alreadyClaimed) {
      return false;
    }
  }

  @Override
  public void release(String consumer, String messageId) {
    if (messageId == null || messageId.isBlank()) {
      return;
    }
    jdbcTemplate.update(delete, Objects.requireNonNull(consumer, "consumer"), messageId);
  }

  @Override
  public long purgeClaimedBefore(Instant cutoff) {
    return jdbcTemplate.update(purge, Timestamp.from(Objects.requireNonNull(cutoff, "cutoff")));
  }
}
