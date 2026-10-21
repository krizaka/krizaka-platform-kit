package com.krizaka.appconfig;

import com.krizaka.appconfig.model.AppConfig;
import com.krizaka.appconfig.store.AppConfigStore;
import java.util.List;
import java.util.Objects;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;

/**
 * The {@code appConfig} component of {@code /actuator/health}: reads the parameters from the store
 * itself, never from the reader's cache. DOWN when the store does not answer — the public endpoint
 * and the {@code 426} filter then serve the last parameters they cached, and stop once the cache
 * expires. UP otherwise, with the platforms configured and those in maintenance (a maintenance is a
 * decision, not an outage).
 */
public final class AppConfigHealthIndicator implements HealthIndicator {

  private final AppConfigStore store;

  /**
   * The indicator over a store.
   *
   * @param store the parameters
   */
  public AppConfigHealthIndicator(AppConfigStore store) {
    this.store = Objects.requireNonNull(store, "store");
  }

  @Override
  public Health health() {
    try {
      List<AppConfig> all = store.findAll();
      return Health.up()
          .withDetail("store", store.getClass().getSimpleName())
          .withDetail("platforms", all.stream().map(c -> c.platform().name()).toList())
          .withDetail(
              "maintenance",
              all.stream().filter(AppConfig::maintenanceOn).map(c -> c.platform().name()).toList())
          .build();
    } catch (RuntimeException e) {
      return Health.down()
          .withDetail("store", store.getClass().getSimpleName())
          .withDetail("error", e.getClass().getName())
          .build();
    }
  }
}
