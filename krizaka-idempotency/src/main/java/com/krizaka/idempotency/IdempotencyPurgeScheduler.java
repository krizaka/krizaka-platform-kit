package com.krizaka.idempotency;

import java.time.Clock;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;

/** Forgets the keys older than the time-to-live, on one daemon thread, with the context. */
public final class IdempotencyPurgeScheduler implements SmartLifecycle {

  private static final Logger log = LoggerFactory.getLogger(IdempotencyPurgeScheduler.class);

  private final IdempotencyStore store;
  private final Duration ttl;
  private final Duration interval;
  private final Clock clock;
  private ScheduledExecutorService executor;

  /**
   * The scheduler.
   *
   * @param store the keys
   * @param ttl how long a key is kept
   * @param interval the delay between two purges
   * @param clock the clock
   */
  public IdempotencyPurgeScheduler(
      IdempotencyStore store, Duration ttl, Duration interval, Clock clock) {
    this.store = Objects.requireNonNull(store, "store");
    this.ttl = Objects.requireNonNull(ttl, "ttl");
    this.interval = Objects.requireNonNull(interval, "interval");
    this.clock = Objects.requireNonNull(clock, "clock");
  }

  /**
   * Forgets the expired keys now.
   *
   * @return how many were forgotten
   */
  public long purge() {
    long purged = store.purgeBefore(clock.instant().minus(ttl));
    if (purged > 0) {
      log.info("Purged {} expired idempotency keys", purged);
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
              Thread thread = new Thread(runnable, "krizaka-idempotency-purge");
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
      log.error("Idempotency purge failed — retrying on the next tick", e);
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
