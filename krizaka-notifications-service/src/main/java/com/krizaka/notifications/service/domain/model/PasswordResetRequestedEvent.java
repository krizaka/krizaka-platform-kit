package com.krizaka.notifications.service.domain.model;

import java.util.Objects;

/**
 * Contract copy of the identity context's {@code evt.password.reset} event (AGENTS.md §6 — no
 * shared messaging jar across service boundaries). The plaintext token is transient: it is only
 * placed in the reset link and never stored or logged.
 *
 * @param email the address the reset was requested for
 * @param plaintextToken the single-use reset token
 */
public record PasswordResetRequestedEvent(String email, String plaintextToken) {

  public PasswordResetRequestedEvent {
    Objects.requireNonNull(email, "email is required");
    Objects.requireNonNull(plaintextToken, "plaintextToken is required");
  }
}
