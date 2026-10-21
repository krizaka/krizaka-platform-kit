package com.krizaka.messaging.outbox;

import java.nio.charset.StandardCharsets;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/**
 * The outbox written once: a PostgreSQL table ({@code
 * com/krizaka/messaging/outbox/schema-postgresql.sql}) and the store the relay needs over it, so a
 * service no longer writes its own.
 *
 * <ul>
 *   <li>{@link #append} inserts the row in the caller's transaction, due now;
 *   <li>it <b>leases</b>: {@link #leaseBatch} claims due rows in one short statement ({@code FOR
 *       UPDATE SKIP LOCKED}, then {@code next_attempt_at = now() + lease}), so the relay publishes
 *       outside any transaction and two instances never take the same row; rows come out in the
 *       order they were written;
 *   <li>a failure backs the row off exponentially — 1 s, 2 s, 4 s… up to {@code maxBackoff};
 *   <li>published rows are deleted after the relay's retention, through the index on {@code
 *       published_at}.
 * </ul>
 */
public final class JdbcOutboxStore implements OutboxStore {

  /** The default table. */
  public static final String DEFAULT_TABLE = "krizaka_outbox";

  private static final Pattern IDENTIFIER =
      Pattern.compile("[A-Za-z_][A-Za-z0-9_]*(\\.[A-Za-z_][A-Za-z0-9_]*)?");

  private static final TypeReference<Map<String, String>> HEADERS = new TypeReference<>() {};

  private final JdbcTemplate jdbc;
  private final JsonMapper json;
  private final long maxBackoffSeconds;
  private final String insert;
  private final String lock;
  private final String lease;
  private final String published;
  private final String failed;
  private final String purge;

  /**
   * A store over a table.
   *
   * @param jdbc the database
   * @param json the mapper the headers are stored with
   * @param table the table, optionally schema-qualified
   * @param maxBackoff the longest wait after a failed publish
   */
  public JdbcOutboxStore(JdbcTemplate jdbc, JsonMapper json, String table, Duration maxBackoff) {
    this.jdbc = Objects.requireNonNull(jdbc, "jdbc");
    this.json = Objects.requireNonNull(json, "json");
    if (table == null || !IDENTIFIER.matcher(table).matches()) {
      throw new IllegalArgumentException("not a table name: " + table);
    }
    if (maxBackoff == null || maxBackoff.toSeconds() < 1) {
      throw new IllegalArgumentException("maxBackoff must be at least one second");
    }
    this.maxBackoffSeconds = maxBackoff.toSeconds();
    String columns =
        "id, exchange, routing_key, message_id, payload::text, headers::text, attempts";
    String due =
        " FROM "
            + table
            + " WHERE published_at IS NULL AND next_attempt_at <= now()"
            + " ORDER BY seq LIMIT ? FOR UPDATE SKIP LOCKED";
    insert =
        "INSERT INTO "
            + table
            + " (id, exchange, routing_key, message_id, payload, headers)"
            + " VALUES (?, ?, ?, ?, ?::jsonb, ?::jsonb)";
    lock = "SELECT " + columns + due;
    // Rows come out in the order they were written: by seq, an identity (two rows appended in the
    // same
    // microsecond tie on created_at). UPDATE … RETURNING keeps no order, so the leased rows are
    // sorted
    // again.
    lease =
        "WITH leased AS (UPDATE "
            + table
            + " SET next_attempt_at = now() + make_interval(secs => ?)"
            + " WHERE id IN (SELECT id"
            + due
            + ") RETURNING "
            + columns
            + ", seq) SELECT "
            + "id, exchange, routing_key, message_id, payload, headers, attempts"
            + " FROM leased ORDER BY seq";
    published = "UPDATE " + table + " SET published_at = now() WHERE id = ?";
    failed =
        "UPDATE "
            + table
            + " SET attempts = attempts + 1, next_attempt_at = now() + make_interval(secs => ?)"
            + " WHERE id = ?";
    purge = "DELETE FROM " + table + " WHERE published_at < ?";
  }

  @Override
  public void append(NewOutboxMessage message) {
    jdbc.update(
        insert,
        UUID.randomUUID(),
        message.exchange(),
        message.routingKey(),
        message.messageId(),
        new String(message.body(), StandardCharsets.UTF_8),
        json.writeValueAsString(message.headers()));
  }

  @Override
  public List<OutboxMessage> lockPendingBatch(int batchSize) {
    return jdbc.query(lock, this::row, batchSize);
  }

  @Override
  public boolean leasesRows() {
    return true;
  }

  @Override
  public List<OutboxMessage> leaseBatch(int batchSize, Duration lease) {
    return jdbc.query(this.lease, this::row, (double) lease.toMillis() / 1000, batchSize);
  }

  @Override
  public void markPublished(UUID id) {
    jdbc.update(published, id);
  }

  @Override
  public void recordFailure(UUID id, int previousAttempts) {
    jdbc.update(failed, (double) backoffSeconds(previousAttempts), id);
  }

  /**
   * The wait after the {@code previousAttempts + 1}-th failure: 1 s, 2 s, 4 s… capped.
   *
   * @param previousAttempts the failures before this one
   * @return the wait, in seconds
   */
  long backoffSeconds(int previousAttempts) {
    int exponent = Math.min(Math.max(previousAttempts, 0), 30);
    return Math.min(1L << exponent, maxBackoffSeconds);
  }

  @Override
  public long purgePublishedBefore(Instant cutoff) {
    return jdbc.update(purge, Timestamp.from(cutoff));
  }

  private OutboxMessage row(ResultSet rs, int row) throws SQLException {
    return new OutboxMessage(
        rs.getObject("id", UUID.class),
        rs.getString("exchange"),
        rs.getString("routing_key"),
        rs.getString("message_id"),
        rs.getString("payload").getBytes(StandardCharsets.UTF_8),
        rs.getInt("attempts"),
        json.readValue(rs.getString("headers"), HEADERS));
  }
}
