package com.krizaka.appconfig.model;

import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Locale;
import java.util.Optional;

/** What the app does about a newer version. */
public enum UpdateMode {
  /** Nothing: the installed version is current, or the operator wants no prompt. */
  NONE,
  /** A dismissible invitation to update. */
  SOFT,
  /** A blocking screen: the app cannot be used until it is updated. */
  FORCE;

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
   * @param text the stored value, possibly {@code null}
   * @return the mode, or empty
   */
  public static Optional<UpdateMode> parse(String text) {
    if (text == null) {
      return Optional.empty();
    }
    for (UpdateMode mode : values()) {
      if (mode.wire().equals(text.trim().toLowerCase(Locale.ROOT))) {
        return Optional.of(mode);
      }
    }
    return Optional.empty();
  }
}
