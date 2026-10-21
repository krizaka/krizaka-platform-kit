package com.krizaka.messaging.outbox;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * How the outbox relay runs ({@code krizaka.messaging.outbox}).
 *
 * <p>Every default is declared on its component with {@link DefaultValue}, so the generated
 * configuration metadata (and the IDE that reads it) shows it, and repeated in the constructor for
 * code that builds the record by hand.
 *
 * @param enabled Whether a relay is scheduled for each OutboxStore bean.
 * @param pollInterval Delay between two batches of pending rows.
 * @param batchSize Most rows one batch claims and publishes.
 * @param purgeInterval Delay between two purges of published rows.
 * @param retention How long published rows are kept before the purge deletes them.
 * @param lease How long a row claimed by a leasing store stays invisible to the other relays. Keep
 *     it longer than a batch takes to publish, or two relays publish the same row.
 * @param failureThreshold Consecutive failures (batches that threw, publishes that failed) after
 *     which the outbox health indicator reports DOWN; reset by the next success.
 */
@ConfigurationProperties(prefix = "krizaka.messaging.outbox")
public record OutboxProperties(
    @DefaultValue("true") Boolean enabled,
    @DefaultValue("500ms") Duration pollInterval,
    @DefaultValue("100") Integer batchSize,
    @DefaultValue("1h") Duration purgeInterval,
    @DefaultValue("7d") Duration retention,
    @DefaultValue("2m") Duration lease,
    @DefaultValue("10") Integer failureThreshold) {

  /** Applies the defaults and refuses non-positive values. */
  @ConstructorBinding
  public OutboxProperties {
    enabled = enabled == null || enabled;
    pollInterval = pollInterval == null ? Duration.ofMillis(500) : pollInterval;
    batchSize = batchSize == null ? 100 : batchSize;
    purgeInterval = purgeInterval == null ? Duration.ofHours(1) : purgeInterval;
    retention = retention == null ? Duration.ofDays(7) : retention;
    lease = lease == null ? Duration.ofMinutes(2) : lease;
    failureThreshold = failureThreshold == null ? 10 : failureThreshold;
    if (failureThreshold < 1) {
      throw new IllegalArgumentException(
          "krizaka.messaging.outbox.failure-threshold must be at least 1");
    }
    if (!isPositive(pollInterval)
        || !isPositive(purgeInterval)
        || !isPositive(retention)
        || !isPositive(lease)) {
      throw new IllegalArgumentException("outbox intervals, retention and lease must be positive");
    }
    if (batchSize < 1) {
      throw new IllegalArgumentException("krizaka.messaging.outbox.batch-size must be positive");
    }
  }

  /**
   * The properties without a lease (the default, two minutes) — the constructor of 0.2.
   *
   * @param enabled whether the relay is scheduled at all
   * @param pollInterval the delay between two batches
   * @param batchSize the most rows per batch
   * @param purgeInterval the delay between two purges of published rows
   * @param retention how long published rows are kept
   */
  public OutboxProperties(
      Boolean enabled,
      Duration pollInterval,
      Integer batchSize,
      Duration purgeInterval,
      Duration retention) {
    this(enabled, pollInterval, batchSize, purgeInterval, retention, null, null);
  }

  private static boolean isPositive(Duration duration) {
    return !duration.isNegative() && !duration.isZero();
  }
}
