package com.krizaka.appconfig.model;

import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Locale;
import java.util.Optional;

/**
 * The platforms an app runs on, as {@code X-Client-Platform} and the {@code platform} query say.
 */
public enum ClientPlatform {
  /** An iPhone or iPad app. */
  IOS,
  /** An Android app. */
  ANDROID,
  /** A macOS app. */
  MACOS,
  /** A Windows app. */
  WINDOWS,
  /** A Linux app. */
  LINUX,
  /** A web app (served by the backend itself, so versioned with it). */
  WEB;

  /**
   * The wire value.
   *
   * @return the lower-case name
   */
  @JsonValue
  public String wire() {
    return name().toLowerCase(Locale.ROOT);
  }

  /**
   * Reads a wire value.
   *
   * @param text the header or query value, possibly {@code null}
   * @return the platform, or empty when it is none of the known ones
   */
  public static Optional<ClientPlatform> parse(String text) {
    if (text == null) {
      return Optional.empty();
    }
    String wanted = text.trim().toLowerCase(Locale.ROOT);
    for (ClientPlatform platform : values()) {
      if (platform.wire().equals(wanted)) {
        return Optional.of(platform);
      }
    }
    return Optional.empty();
  }
}
