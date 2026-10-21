package com.krizaka.idempotency;

import java.util.Objects;

/**
 * What a caller asks to run once.
 *
 * @param scope what is run, e.g. {@code orders.create} or {@code installation:42}; a key is unique
 *     within its scope and subject; 1 to 100 characters
 * @param subject who asks — the authenticated principal, an API key id; a key of one subject never
 *     replays another's response; 1 to 255 characters
 * @param key the caller's {@code Idempotency-Key}; {@code null} or blank runs the action without
 *     any guarantee, as if the header were absent
 * @param body what is asked (the request body, its parameters); its canonical JSON is
 *     fingerprinted, so the order of fields is not a difference; {@code null} for none
 */
public record IdempotentRequest(String scope, String subject, String key, Object body) {

  /** Refuses a request that cannot be keyed. */
  public IdempotentRequest {
    requireBounded(scope, 100, "scope");
    requireBounded(subject, 255, "subject");
  }

  /**
   * Whether the caller sent a key.
   *
   * @return {@code false} when the key is {@code null} or blank
   */
  public boolean keyed() {
    return key != null && !key.isBlank();
  }

  private static void requireBounded(String value, int max, String name) {
    Objects.requireNonNull(value, name);
    if (value.isBlank() || value.length() > max) {
      throw new IllegalArgumentException(name + " must be 1 to " + max + " characters");
    }
  }
}
