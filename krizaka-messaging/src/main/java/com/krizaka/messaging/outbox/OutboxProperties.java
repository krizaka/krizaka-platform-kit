package com.krizaka.messaging.outbox;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * How the outbox relay runs ({@code krizaka.messaging.outbox}).
 *
 * @param enabled whether the relay is scheduled at all; default {@code true}
 * @param pollInterval the delay between two batches; default 500 ms
 * @param batchSize the most rows per batch; default 100
 * @param purgeInterval the delay between two purges of published rows; default 1 hour
 * @param retention how long published rows are kept; default 7 days
 */
@ConfigurationProperties(prefix = "krizaka.messaging.outbox")
public record OutboxProperties(
    Boolean enabled,
    Duration pollInterval,
    Integer batchSize,
    Duration purgeInterval,
    Duration retention) {

  /** Applies the defaults and refuses non-positive values. */
  public OutboxProperties {
    enabled = enabled == null || enabled;
    pollInterval = pollInterval == null ? Duration.ofMillis(500) : pollInterval;
    batchSize = batchSize == null ? 100 : batchSize;
    purgeInterval = purgeInterval == null ? Duration.ofHours(1) : purgeInterval;
    retention = retention == null ? Duration.ofDays(7) : retention;
    if (!isPositive(pollInterval) || !isPositive(purgeInterval) || !isPositive(retention)) {
      throw new IllegalArgumentException("outbox intervals and retention must be positive");
    }
    if (batchSize < 1) {
      throw new IllegalArgumentException("krizaka.messaging.outbox.batch-size must be positive");
    }
  }

  private static boolean isPositive(Duration duration) {
    return !duration.isNegative() && !duration.isZero();
  }
}
