package com.krizaka.messaging.outbox;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;

/**
 * The {@code outbox} component of {@code /actuator/health}: one entry per relayed store.
 *
 * <p>DOWN when a relay has failed {@code failure-threshold} times in a row (a batch that threw —
 * the database —, a publish that failed — the broker): its rows are piling up and nothing is
 * delivered. UP otherwise, with the last success and the current run of failures as details, so a
 * relay that is struggling shows it before it is down. A relay that has not run yet is UP: it has
 * failed nothing.
 */
public final class OutboxHealthIndicator implements HealthIndicator {

  private final OutboxRelayScheduler scheduler;
  private final int failureThreshold;

  /**
   * The indicator over the scheduled relays.
   *
   * @param scheduler the relays
   * @param failureThreshold consecutive failures after which a relay is DOWN; at least 1
   */
  public OutboxHealthIndicator(OutboxRelayScheduler scheduler, int failureThreshold) {
    this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
    if (failureThreshold < 1) {
      throw new IllegalArgumentException("failureThreshold must be at least 1");
    }
    this.failureThreshold = failureThreshold;
  }

  @Override
  public Health health() {
    List<OutboxRelay.Status> relays = scheduler.statuses();
    boolean down = false;
    Map<String, Object> stores = new LinkedHashMap<>();
    for (OutboxRelay.Status status : relays) {
      Map<String, Object> detail = new LinkedHashMap<>();
      detail.put("lastSuccess", status.lastSuccess() == null ? "never" : status.lastSuccess());
      detail.put("consecutiveFailures", status.consecutiveFailures());
      if (status.lastError() != null) {
        detail.put("lastError", status.lastError());
      }
      stores.put(status.store(), detail);
      down |= status.consecutiveFailures() >= failureThreshold;
    }
    Health.Builder health = down ? Health.down() : Health.up();
    return health
        .withDetail("failureThreshold", failureThreshold)
        .withDetail("stores", stores)
        .build();
  }
}
