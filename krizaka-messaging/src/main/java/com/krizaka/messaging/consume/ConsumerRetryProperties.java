package com.krizaka.messaging.consume;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * How a failing listener is retried before its message goes to the dead-letter queue ({@code
 * krizaka.messaging.retry}).
 *
 * <pre>{@code
 * krizaka:
 *   messaging:
 *     retry:
 *       max-attempts: 5     # deliveries to the listener, the first included
 *       initial: 500ms      # wait before the second attempt
 *       multiplier: 2       # each wait is the previous one times this
 *       max: 10s            # cap on one wait
 * }</pre>
 *
 * @param max Longest wait between two attempts, not shorter than the initial wait.
 * @param maxAttempts How many times the listener runs before the message is dead-lettered, the
 *     first delivery included; at least 1.
 * @param initial Wait before the second attempt.
 * @param multiplier Factor between two consecutive waits; at least 1.
 */
@ConfigurationProperties(prefix = "krizaka.messaging.retry")
public record ConsumerRetryProperties(
    @DefaultValue("5") Integer maxAttempts,
    @DefaultValue("500ms") Duration initial,
    @DefaultValue("2") Double multiplier,
    @DefaultValue("10s") Duration max) {

  /** Applies the defaults and refuses a policy that could not run. */
  public ConsumerRetryProperties {
    maxAttempts = maxAttempts == null ? 5 : maxAttempts;
    initial = initial == null ? Duration.ofMillis(500) : initial;
    multiplier = multiplier == null ? 2.0 : multiplier;
    max = max == null ? Duration.ofSeconds(10) : max;
    if (maxAttempts < 1) {
      throw new IllegalArgumentException("krizaka.messaging.retry.max-attempts must be at least 1");
    }
    if (initial.isNegative() || initial.isZero()) {
      throw new IllegalArgumentException("krizaka.messaging.retry.initial must be positive");
    }
    if (multiplier < 1.0) {
      throw new IllegalArgumentException("krizaka.messaging.retry.multiplier must be at least 1");
    }
    if (max.compareTo(initial) < 0) {
      throw new IllegalArgumentException(
          "krizaka.messaging.retry.max cannot be shorter than krizaka.messaging.retry.initial");
    }
  }

  /**
   * The defaults: 5 attempts, 500 ms, ×2, 10 s.
   *
   * @return the default policy
   */
  public static ConsumerRetryProperties defaults() {
    return new ConsumerRetryProperties(null, null, null, null);
  }
}
