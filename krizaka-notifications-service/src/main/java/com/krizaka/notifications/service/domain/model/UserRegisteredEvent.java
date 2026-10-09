package com.krizaka.notifications.service.domain.model;

import java.util.Map;
import java.util.Objects;

/**
 * Contract copy of the identity context's {@code evt.user.registered} event (AGENTS.md §6 — no
 * shared messaging jar across service boundaries): only the subset of fields this consumer reads.
 * Unknown fields in the wire payload are ignored by the JSON converter.
 *
 * @param user the registered user's notification coordinates
 * @param plaintextToken the single-use verification token, when verification is enabled
 */
public record UserRegisteredEvent(UserSummary user, String plaintextToken) {

  public UserRegisteredEvent {
    Objects.requireNonNull(user, "user is required");
  }

  /** {@code true} when identity issued a verification token for this registration. */
  public boolean requiresVerification() {
    return plaintextToken != null && !plaintextToken.isBlank();
  }

  /**
   * The slice of the registered user this consumer needs.
   *
   * @param email the address the verification notification goes to
   * @param username the display name used in the greeting
   * @param preferences the user's preferences; {@code language} selects the template locale
   */
  public record UserSummary(String email, String username, Map<String, Object> preferences) {

    public UserSummary {
      Objects.requireNonNull(email, "email is required");
      username = (username == null || username.isBlank()) ? email : username;
      preferences = (preferences == null) ? Map.of() : Map.copyOf(preferences);
    }

    /** The user's preferred language, {@code en} when none was recorded. */
    public String language() {
      Object language = preferences.get("language");
      return (language == null || String.valueOf(language).isBlank())
          ? "en"
          : String.valueOf(language);
    }
  }
}
