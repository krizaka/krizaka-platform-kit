package com.krizaka.messaging.outbox;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;

/**
 * Runs every {@link OutboxRelay} on a fixed delay, on one dedicated thread, for the lifetime of the
 * application context.
 *
 * <p>Self-contained on purpose: it needs neither {@code @EnableScheduling} nor the application's
 * task scheduler, so adding the kit never changes how the application's own {@code @Scheduled}
 * methods run. A fixed <i>delay</i> (not rate) means a slow batch is never overlapped by the next.
 */
public final class OutboxRelayScheduler implements SmartLifecycle {

  private static final Logger log = LoggerFactory.getLogger(OutboxRelayScheduler.class);

  private final List<OutboxRelay> relays;
  private final Duration pollInterval;
  private final Duration purgeInterval;
  private ScheduledExecutorService executor;

  /**
   * A scheduler over the given relays.
   *
   * @param relays one per outbox store
   * @param pollInterval the delay between two batches
   * @param purgeInterval the delay between two purges
   */
  public OutboxRelayScheduler(
      List<OutboxRelay> relays, Duration pollInterval, Duration purgeInterval) {
    this.relays = List.copyOf(relays);
    this.pollInterval = Objects.requireNonNull(pollInterval, "pollInterval");
    this.purgeInterval = Objects.requireNonNull(purgeInterval, "purgeInterval");
  }

  @Override
  public synchronized void start() {
    if (executor != null || relays.isEmpty()) {
      return;
    }
    executor =
        Executors.newSingleThreadScheduledExecutor(
            runnable -> {
              Thread thread = new Thread(runnable, "krizaka-outbox-relay");
              thread.setDaemon(true);
              return thread;
            });
    executor.scheduleWithFixedDelay(
        () -> relays.forEach(relay -> guarded(relay::relayPendingBatch, relay, "relay")),
        pollInterval.toMillis(),
        pollInterval.toMillis(),
        TimeUnit.MILLISECONDS);
    executor.scheduleWithFixedDelay(
        () -> relays.forEach(relay -> guarded(relay::purgePublished, relay, "purge")),
        purgeInterval.toMillis(),
        purgeInterval.toMillis(),
        TimeUnit.MILLISECONDS);
  }

  @Override
  public synchronized void stop() {
    if (executor == null) {
      return;
    }
    executor.shutdown();
    try {
      if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
        executor.shutdownNow();
      }
    } catch (InterruptedException e) {
      executor.shutdownNow();
      Thread.currentThread().interrupt();
    }
    executor = null;
  }

  @Override
  public synchronized boolean isRunning() {
    return executor != null;
  }

  /**
   * A failing batch must not cancel the schedule: a scheduled task that throws never runs again.
   */
  private static void guarded(Runnable task, OutboxRelay relay, String what) {
    try {
      task.run();
    } catch (RuntimeException e) {
      log.error("Outbox {} failed for {} — retrying on the next tick", what, relay.storeName(), e);
    }
  }
}
