package com.krizaka.security.jwt;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * The shared HS256 secret a service validates session tokens with ({@code krizaka.security.jwt}).
 *
 * <p>The identity service signs session and service tokens with this secret; every other service
 * verifies them locally, so authorising a request costs no call to identity on the hot path.
 *
 * <p>The 32-character minimum is enforced here, once, rather than by convention in every service:
 * HS256 is a 256-bit HMAC, and a shorter key is a weaker key that still "works". A service that
 * starts with a short secret is a service that should not start.
 *
 * @param secret the HMAC-SHA256 secret, at least 32 characters (256 bits)
 */
@ConfigurationProperties(prefix = "krizaka.security.jwt")
public record SessionJwtProperties(String secret) {

  /** The minimum secret length, in characters, for a 256-bit HS256 key. */
  public static final int MINIMUM_SECRET_LENGTH = 32;

  /**
   * Rejects a missing or short secret.
   *
   * @throws IllegalArgumentException when the secret is {@code null} or shorter than {@value
   *     #MINIMUM_SECRET_LENGTH} characters
   */
  public SessionJwtProperties {
    if (secret == null || secret.length() < MINIMUM_SECRET_LENGTH) {
      throw new IllegalArgumentException(
          "krizaka.security.jwt.secret must be at least "
              + MINIMUM_SECRET_LENGTH
              + " characters (256-bit HS256)");
    }
  }

  /**
   * Hides the secret from logs, actuator output and exception messages.
   *
   * @return a description that never contains the secret
   */
  @Override
  public String toString() {
    return "SessionJwtProperties[secret=<redacted>]";
  }
}
