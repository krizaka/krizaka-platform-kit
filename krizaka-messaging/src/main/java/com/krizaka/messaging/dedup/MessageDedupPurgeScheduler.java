package com.krizaka.messaging.dedup;

import java.time.Clock;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;

/**
 * Forgets the claims older than {@code krizaka.messaging.dedup.retention}, on one daemon thread,
 * with the context. Until now nothing called {@link MessageDedup#purgeClaimedBefore}: the claims
 * table only grew.
 */
public final class MessageDedupPurgeScheduler implements SmartLifecycle {

  private static final Logger log = LoggerFactory.getLogger(MessageDedupPurgeScheduler.class);

  private final MessageDedup dedup;
  private final Duration retention;
  private final Duration interval;
  private final Clock clock;
  private ScheduledExecutorService executor;

  /**
   * The scheduler.
   *
   * @param dedup the claims
   * @param retention how long a claim is kept — beyond the broker's longest redelivery window
   * @param interval the delay between two purges
   * @param clock the clock
   */
  public MessageDedupPurgeScheduler(
      MessageDedup dedup, Duration retention, Duration interval, Clock clock) {
    this.dedup = Objects.requireNonNull(dedup, "dedup");
    this.retention = Objects.requireNonNull(retention, "retention");
    this.interval = Objects.requireNonNull(interval, "interval");
    this.clock = Objects.requireNonNull(clock, "clock");
  }

  /**
   * Forgets the expired claims now.
   *
   * @return how many were forgotten
   */
  public long purge() {
    long purged = dedup.purgeClaimedBefore(clock.instant().minus(retention));
    if (purged > 0) {
      log.info("Purged {} message claims older than {}", purged, retention);
    }
    return purged;
  }

  @Override
  public synchronized void start() {
    if (executor != null) {
      return;
    }
    executor =
        Executors.newSingleThreadScheduledExecutor(
            runnable -> {
              Thread thread = new Thread(runnable, "krizaka-dedup-purge");
              thread.setDaemon(true);
              return thread;
            });
    executor.scheduleWithFixedDelay(
        this::guardedPurge, interval.toMillis(), interval.toMillis(), TimeUnit.MILLISECONDS);
  }

  private void guardedPurge() {
    try {
      purge();
    } catch (RuntimeException e) {
      log.error("Message claim purge failed — retrying on the next tick", e);
    }
  }

  @Override
  public synchronized void stop() {
    if (executor != null) {
      executor.shutdownNow();
      executor = null;
    }
  }

  @Override
  public synchronized boolean isRunning() {
    return executor != null;
  }
}
