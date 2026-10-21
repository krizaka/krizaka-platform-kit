package com.krizaka.ratelimit;

import java.util.Objects;

/**
 * What a request is limited as: an identity, and optionally its own requests-per-minute (a tier).
 *
 * @param id the identity — an address, an API key, a user id; never logged, only its digest is kept
 * @param requestsPerMinute this key's limit, or {@code null} for the configured default
 */
public record RateLimitKey(String id, Integer requestsPerMinute) {

  /** Checks the identity. */
  public RateLimitKey {
    Objects.requireNonNull(id, "id");
  }

  /**
   * A key with the default limit.
   *
   * @param id the identity
   * @return the key
   */
  public static RateLimitKey of(String id) {
    return new RateLimitKey(id, null);
  }
}
