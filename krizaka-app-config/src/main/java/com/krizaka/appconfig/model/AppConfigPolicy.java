package com.krizaka.appconfig.model;

import java.time.LocalDate;
import java.util.Locale;
import java.util.Map;
import java.util.OptionalInt;

/**
 * The rules, pure: given the parameters and what a client says about itself, what happens.
 *
 * <p>One place decides {@code none | soft | force}, whether a request is refused with {@code 426}
 * and whether the contract it speaks is on its way out, so the endpoint and the filter can never
 * disagree.
 */
public final class AppConfigPolicy {

  /** The language used when the client asks for none the parameters have. */
  public static final String DEFAULT_LANGUAGE = "en";

  private AppConfigPolicy() {}

  /**
   * What the client should do about its version.
   *
   * <p>Below the minimum is {@code force}; below the latest is {@code soft}; otherwise {@code
   * none}. The operator's override wins when set; a client that sent no version gets the override
   * or {@code none}.
   *
   * @param config the platform's parameters
   * @param client the version the client runs, or {@code null} when it did not say
   * @return the mode
   */
  public static UpdateMode updateMode(AppConfig config, AppVersion client) {
    if (config.updateOverride() != null) {
      return config.updateOverride();
    }
    if (client == null) {
      return UpdateMode.NONE;
    }
    if (client.isBefore(config.minSupportedVersion())) {
      return UpdateMode.FORCE;
    }
    return client.isBefore(config.latestVersion()) ? UpdateMode.SOFT : UpdateMode.NONE;
  }

  /**
   * The reason a request must be refused with {@code 426}, if any.
   *
   * @param config the platform's parameters
   * @param client the client's version, or {@code null}
   * @param contract the contract the client speaks, or empty
   * @return the reason, or {@link Verdict#OK}
   */
  public static Verdict verdict(AppConfig config, AppVersion client, OptionalInt contract) {
    if (client != null && client.isBefore(config.minSupportedVersion())) {
      return Verdict.VERSION_TOO_OLD;
    }
    if (contract.isPresent() && contract.getAsInt() < config.contractMin()) {
      return Verdict.CONTRACT_TOO_OLD;
    }
    return Verdict.OK;
  }

  /**
   * Whether the contract a client speaks is still served but on its way out (N-1).
   *
   * @param config the platform's parameters
   * @param contract the contract the client speaks, or empty
   * @return {@code true} when it is older than the newest one and still accepted
   */
  public static boolean deprecated(AppConfig config, OptionalInt contract) {
    return contract.isPresent()
        && contract.getAsInt() < config.contractMax()
        && contract.getAsInt() >= config.contractMin();
  }

  /**
   * The public payload for a client.
   *
   * @param config the platform's parameters
   * @param client the client's version, or {@code null}
   * @param language the language asked for (two letters), or {@code null}
   * @return the payload, its texts in the best language available
   */
  public static AppConfigPayload payload(AppConfig config, AppVersion client, String language) {
    String lang = language == null ? DEFAULT_LANGUAGE : language.toLowerCase(Locale.ROOT);
    String chosen = pickLanguage(lang, config.message(), config.maintenanceMessage());
    return new AppConfigPayload(
        config.platform().wire(),
        config.minSupportedVersion().toString(),
        config.latestVersion().toString(),
        updateMode(config, client).wire(),
        config.storeUrl(),
        new AppConfigPayload.Maintenance(
            config.maintenanceOn(), text(config.maintenanceMessage(), chosen)),
        new AppConfigPayload.Contract(
            config.contractMin(),
            config.contractMax(),
            config.contractSunset() == null ? null : config.contractSunset().toString()),
        config.flags(),
        text(config.message(), chosen),
        chosen);
  }

  /**
   * Whether a sunset date has passed.
   *
   * @param sunset the announced date, or {@code null}
   * @param today today
   * @return {@code true} when the contract is past its sunset
   */
  public static boolean pastSunset(LocalDate sunset, LocalDate today) {
    return sunset != null && today.isAfter(sunset);
  }

  private static String pickLanguage(String wanted, Map<String, String> a, Map<String, String> b) {
    if (a.containsKey(wanted) || b.containsKey(wanted)) {
      return wanted;
    }
    return DEFAULT_LANGUAGE;
  }

  private static String text(Map<String, String> texts, String language) {
    String text = texts.get(language);
    return text != null ? text : texts.get(DEFAULT_LANGUAGE);
  }

  /** Why a request is refused, if it is. */
  public enum Verdict {
    /** Let it through. */
    OK,
    /** The app is older than the minimum supported version. */
    VERSION_TOO_OLD,
    /** The API contract the app speaks is no longer served. */
    CONTRACT_TOO_OLD
  }
}
