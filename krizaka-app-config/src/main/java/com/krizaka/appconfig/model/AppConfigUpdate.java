package com.krizaka.appconfig.model;

import java.time.LocalDate;
import java.util.Map;

/**
 * The body of {@code PUT /api/v1/admin/app-config/{platform}}: the new parameters and why.
 *
 * @param minSupportedVersion the oldest version allowed to run
 * @param latestVersion the newest published version
 * @param updateOverride {@code none}, {@code soft}, {@code force} or {@code null} for automatic
 * @param storeUrl the store page, https only
 * @param maintenanceOn whether the maintenance screen is shown
 * @param maintenanceMessage maintenance text per language
 * @param message update text per language
 * @param contractMin oldest contract served
 * @param contractMax newest contract spoken
 * @param contractSunset announced end of the older contracts, or {@code null}
 * @param flags feature flags
 * @param reason why — required, it goes to the audit trail
 */
public record AppConfigUpdate(
    String minSupportedVersion,
    String latestVersion,
    String updateOverride,
    String storeUrl,
    boolean maintenanceOn,
    Map<String, String> maintenanceMessage,
    Map<String, String> message,
    int contractMin,
    int contractMax,
    LocalDate contractSunset,
    Map<String, Object> flags,
    String reason) {

  /**
   * Validates into the parameters of a platform.
   *
   * @param platform the platform
   * @return the (not yet published) parameters
   * @throws IllegalArgumentException when a bound is broken
   */
  public AppConfig toConfig(ClientPlatform platform) {
    UpdateMode override = null;
    if (updateOverride != null && !updateOverride.isBlank()) {
      override =
          UpdateMode.parse(updateOverride)
              .orElseThrow(
                  () -> new IllegalArgumentException("updateOverride must be none, soft or force"));
    }
    return new AppConfig(
        platform,
        AppVersion.of(minSupportedVersion),
        AppVersion.of(latestVersion),
        override,
        storeUrl,
        maintenanceOn,
        maintenanceMessage,
        message,
        contractMin,
        contractMax,
        contractSunset,
        flags,
        1,
        java.time.Instant.EPOCH,
        "pending");
  }
}
