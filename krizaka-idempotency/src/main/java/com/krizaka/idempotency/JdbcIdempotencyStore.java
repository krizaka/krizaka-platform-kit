package com.krizaka.idempotency;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Keys in the application's PostgreSQL database, in the action's transaction.
 *
 * <p>The claim is one {@code INSERT … ON CONFLICT DO NOTHING}: a second transaction inserting the
 * same key waits on the primary key until the first commits — then it reads the recorded response —
 * or rolls back — then it inserts and runs. The response is written in the same transaction as the
 * action's own writes, so a key never records a response whose effects were rolled back. The table
 * is {@code com/krizaka/idempotency/schema-postgresql.sql}.
 */
public final class JdbcIdempotencyStore implements IdempotencyStore {

  /** The default table name. */
  public static final String DEFAULT_TABLE = "idempotency_keys";

  private static final Pattern IDENTIFIER =
      Pattern.compile("[A-Za-z_][A-Za-z0-9_]*(\\.[A-Za-z_][A-Za-z0-9_]*)?");

  // @fixed: a purge running between the conflict and the read is retried, not looped forever
  private static final int CLAIM_ATTEMPTS = 3;

  private final JdbcTemplate jdbc;
  private final String insert;
  private final String reclaim;
  private final String select;
  private final String complete;
  private final String abandon;
  private final String purge;

  /**
   * A store over a table.
   *
   * @param jdbc the database
   * @param table the table, optionally schema-qualified
   */
  public JdbcIdempotencyStore(JdbcTemplate jdbc, String table) {
    this.jdbc = Objects.requireNonNull(jdbc, "jdbc");
    if (table == null || !IDENTIFIER.matcher(table).matches()) {
      throw new IllegalArgumentException("not a table name: " + table);
    }
    String where = " WHERE scope = ? AND subject = ? AND idem_key = ?";
    insert =
        "INSERT INTO "
            + table
            + " (scope, subject, idem_key, fingerprint, created_at) VALUES (?, ?, ?, ?, ?)"
            + " ON CONFLICT (scope, subject, idem_key) DO NOTHING";
    reclaim =
        "UPDATE "
            + table
            + " SET fingerprint = ?, response = NULL, created_at = ?"
            + where
            + " AND created_at < ?";
    select = "SELECT fingerprint, response FROM " + table + where;
    complete = "UPDATE " + table + " SET response = ?" + where;
    abandon = "DELETE FROM " + table + where;
    purge = "DELETE FROM " + table + " WHERE created_at < ?";
  }

  @Override
  public Optional<Recorded> claim(
      String scope,
      String subject,
      String key,
      String fingerprint,
      Instant now,
      Instant expiredBefore) {
    for (int attempt = 0; attempt < CLAIM_ATTEMPTS; attempt++) {
      if (jdbc.update(insert, scope, subject, key, fingerprint, Timestamp.from(now)) == 1) {
        return Optional.empty();
      }
      if (jdbc.update(
              reclaim,
              fingerprint,
              Timestamp.from(now),
              scope,
              subject,
              key,
              Timestamp.from(expiredBefore))
          == 1) {
        return Optional.empty();
      }
      List<Recorded> recorded =
          jdbc.query(
              select,
              (rs, row) -> new Recorded(rs.getString("fingerprint"), rs.getString("response")),
              scope,
              subject,
              key);
      if (!recorded.isEmpty()) {
        return Optional.of(recorded.getFirst());
      }
    }
    throw new IllegalStateException("the key could not be claimed nor read: " + key);
  }

  @Override
  public void complete(String scope, String subject, String key, String response) {
    jdbc.update(complete, response, scope, subject, key);
  }

  @Override
  public void abandon(String scope, String subject, String key) {
    jdbc.update(abandon, scope, subject, key);
  }

  @Override
  public long purgeBefore(Instant cutoff) {
    return jdbc.update(purge, Timestamp.from(cutoff));
  }

  @Override
  public boolean transactional() {
    return true;
  }
}
