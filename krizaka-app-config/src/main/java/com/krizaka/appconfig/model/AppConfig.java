package com.krizaka.appconfig.model;

import java.net.URI;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * The parameters of one platform, as the database holds them (and the admin edits them).
 *
 * <p>Every value that decides what an app does about its version lives here, never in code. The
 * constructor enforces the safety bounds, so a stored or edited row can never lock everybody out by
 * accident: the minimum cannot exceed the latest version, the contract range cannot be empty, a
 * store URL must be {@code https}.
 *
 * @param platform the platform these parameters are for
 * @param minSupportedVersion the oldest version still allowed to run; older ones get {@code 426}
 * @param latestVersion the newest version published in the store
 * @param updateOverride the operator's override of the computed mode, {@code null} for automatic
 *     ({@code force} for everybody is the kill switch; {@code none} silences the prompts)
 * @param storeUrl where to get the update, {@code https} only, or {@code null}
 * @param maintenanceOn whether the maintenance screen is shown
 * @param maintenanceMessage the maintenance text per language ({@code en}, {@code fr}…)
 * @param message the update text per language
 * @param contractMin the oldest API contract still served
 * @param contractMax the newest API contract this backend speaks
 * @param contractSunset when contracts older than {@code contractMax} stop being served, or {@code
 *     null} when it is not announced
 * @param flags the feature flags, boolean, number or string values
 * @param revision the number of the published revision, 1 for the seed
 * @param updatedAt when this revision was published
 * @param updatedBy who published it
 */
public record AppConfig(
    ClientPlatform platform,
    AppVersion minSupportedVersion,
    AppVersion latestVersion,
    UpdateMode updateOverride,
    String storeUrl,
    boolean maintenanceOn,
    Map<String, String> maintenanceMessage,
    Map<String, String> message,
    int contractMin,
    int contractMax,
    LocalDate contractSunset,
    Map<String, Object> flags,
    int revision,
    Instant updatedAt,
    String updatedBy) {

  private static final Pattern LANGUAGE = Pattern.compile("[a-z]{2}");
  private static final Pattern FLAG_KEY = Pattern.compile("[A-Za-z][A-Za-z0-9._-]{0,63}");
  private static final int MAX_TEXT = 500;

  /**
   * Copies the maps and checks the bounds.
   *
   * @throws IllegalArgumentException when a bound is broken
   */
  public AppConfig {
    if (platform == null || minSupportedVersion == null || latestVersion == null) {
      throw new IllegalArgumentException("platform and versions are required");
    }
    if (latestVersion.isBefore(minSupportedVersion)) {
      throw new IllegalArgumentException(
          "latestVersion "
              + latestVersion
              + " is older than minSupportedVersion "
              + minSupportedVersion);
    }
    if (contractMin < 1 || contractMax < contractMin) {
      throw new IllegalArgumentException(
          "contract range must satisfy 1 <= min <= max, got " + contractMin + ".." + contractMax);
    }
    if (storeUrl != null && !storeUrl.isBlank()) {
      URI uri = URI.create(storeUrl);
      if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null) {
        throw new IllegalArgumentException("storeUrl must be an absolute https URL");
      }
    } else {
      storeUrl = null;
    }
    maintenanceMessage = texts("maintenanceMessage", maintenanceMessage);
    message = texts("message", message);
    flags = flags(flags);
    if (revision < 1) {
      throw new IllegalArgumentException("revision starts at 1");
    }
  }

  /**
   * The permissive parameters a platform starts with: no minimum, no maintenance, one contract.
   *
   * @param platform the platform
   * @return version 1 of the parameters
   */
  public static AppConfig defaults(ClientPlatform platform) {
    return new AppConfig(
        platform,
        AppVersion.ZERO,
        AppVersion.ZERO,
        null,
        null,
        false,
        Map.of(),
        Map.of(),
        1,
        1,
        null,
        Map.of(),
        1,
        Instant.EPOCH,
        "seed");
  }

  /**
   * The same parameters, republished.
   *
   * @param revision the new revision number
   * @param at when it was published
   * @param by who published it
   * @return a copy carrying the new revision
   */
  public AppConfig published(int revision, Instant at, String by) {
    return new AppConfig(
        platform,
        minSupportedVersion,
        latestVersion,
        updateOverride,
        storeUrl,
        maintenanceOn,
        maintenanceMessage,
        message,
        contractMin,
        contractMax,
        contractSunset,
        flags,
        revision,
        at,
        by);
  }

  private static Map<String, String> texts(String field, Map<String, String> source) {
    Map<String, String> copy = new LinkedHashMap<>();
    if (source != null) {
      source.forEach(
          (language, text) -> {
            if (!LANGUAGE.matcher(language).matches()) {
              throw new IllegalArgumentException(
                  field + " language must be two letters: " + language);
            }
            if (text != null && !text.isBlank()) {
              if (text.length() > MAX_TEXT) {
                throw new IllegalArgumentException(field + "." + language + " is too long");
              }
              copy.put(language, text);
            }
          });
    }
    return Map.copyOf(copy);
  }

  private static Map<String, Object> flags(Map<String, Object> source) {
    Map<String, Object> copy = new LinkedHashMap<>();
    if (source != null) {
      source.forEach(
          (key, value) -> {
            if (!FLAG_KEY.matcher(key).matches()) {
              throw new IllegalArgumentException("flag key not allowed: " + key);
            }
            if (!(value instanceof Boolean || value instanceof Number || value instanceof String)) {
              throw new IllegalArgumentException(
                  "flag " + key + " must be a boolean, number or string");
            }
            copy.put(key, value);
          });
    }
    return Map.copyOf(copy);
  }
}
