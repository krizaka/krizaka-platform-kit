package com.krizaka.appconfig.store;

import com.krizaka.appconfig.model.AppConfig;
import com.krizaka.appconfig.model.AppConfigChange;
import com.krizaka.appconfig.model.ClientPlatform;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** A store in memory: for tests and demos, never for a service with more than one instance. */
public final class InMemoryAppConfigStore implements AppConfigStore {

  private final Clock clock;
  private final Map<ClientPlatform, List<AppConfigChange>> trail =
      new EnumMap<>(ClientPlatform.class);

  /**
   * Creates an empty store.
   *
   * @param clock the clock stamping revisions
   */
  public InMemoryAppConfigStore(Clock clock) {
    this.clock = Objects.requireNonNull(clock, "clock");
  }

  @Override
  public synchronized Optional<AppConfig> find(ClientPlatform platform) {
    List<AppConfigChange> changes = trail.get(platform);
    return changes == null || changes.isEmpty()
        ? Optional.empty()
        : Optional.of(changes.getLast().config());
  }

  @Override
  public synchronized List<AppConfig> findAll() {
    return trail.keySet().stream()
        .sorted(Comparator.naturalOrder())
        .map(platform -> find(platform).orElseThrow())
        .toList();
  }

  @Override
  public synchronized AppConfig publish(AppConfig config, String actor, String reason) {
    requireReason(reason);
    int revision = find(config.platform()).map(current -> current.revision() + 1).orElse(1);
    AppConfig published = config.published(revision, clock.instant(), actor);
    trail
        .computeIfAbsent(config.platform(), key -> new ArrayList<>())
        .add(new AppConfigChange(published, reason, actor, published.updatedAt()));
    return published;
  }

  @Override
  public synchronized List<AppConfigChange> history(ClientPlatform platform) {
    List<AppConfigChange> changes = new ArrayList<>(trail.getOrDefault(platform, List.of()));
    java.util.Collections.reverse(changes);
    return List.copyOf(changes);
  }

  @Override
  public synchronized Optional<AppConfig> rollback(
      ClientPlatform platform, int revision, String actor, String reason) {
    return trail.getOrDefault(platform, List.of()).stream()
        .filter(change -> change.config().revision() == revision)
        .findFirst()
        .map(change -> publish(change.config(), actor, reason));
  }

  static void requireReason(String reason) {
    if (reason == null || reason.isBlank()) {
      throw new IllegalArgumentException("a reason is required: it goes to the audit trail");
    }
  }
}
