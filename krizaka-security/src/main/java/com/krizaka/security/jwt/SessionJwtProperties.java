package com.krizaka.security.jwt;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * How a service verifies session tokens ({@code krizaka.security.jwt}): either against the issuer's
 * published keys (preferred) or against one shared HS256 secret (legacy, see ADR-001).
 *
 * <p>The two modes are mutually exclusive and exactly one must be set: a service that starts with
 * both, or with neither, should not start. In {@code jwk-set-uri} mode the service holds no signing
 * material, so a leaked configuration cannot mint a token.
 *
 * @param secret Legacy shared HMAC-SHA256 secret, at least 32 characters (256 bits). Whoever can
 *     verify with it can also mint.
 * @param jwkSetUri The issuer's JWKS endpoint ({@code /.well-known/jwks.json}). Only ES256 tokens
 *     are accepted in this mode; keys are matched by {@code kid}, and an unknown {@code kid}
 *     triggers a (rate limited) refresh so rotation needs no restart.
 * @param issuer Required with {@code jwk-set-uri}: the only {@code iss} accepted.
 * @param audience Optional with {@code jwk-set-uri}: when set, the {@code aud} that must be
 *     present.
 */
@ConfigurationProperties(prefix = "krizaka.security.jwt")
public record SessionJwtProperties(
    String secret, String jwkSetUri, String issuer, String audience) {

  /** The minimum secret length, in characters, for a 256-bit HS256 key. */
  public static final int MINIMUM_SECRET_LENGTH = 32;

  /**
   * Validates the mode.
   *
   * @throws IllegalArgumentException when both or neither mode is set, the secret is short, or
   *     {@code jwk-set-uri} comes without an {@code issuer}
   */
  public SessionJwtProperties {
    secret = blankToNull(secret);
    jwkSetUri = blankToNull(jwkSetUri);
    issuer = blankToNull(issuer);
    audience = blankToNull(audience);
    if (secret != null && jwkSetUri != null) {
      throw new IllegalArgumentException(
          "krizaka.security.jwt.secret and krizaka.security.jwt.jwk-set-uri are mutually exclusive");
    }
    if (secret == null && jwkSetUri == null) {
      throw new IllegalArgumentException(
          "set krizaka.security.jwt.jwk-set-uri (preferred) or krizaka.security.jwt.secret");
    }
    if (secret != null && secret.length() < MINIMUM_SECRET_LENGTH) {
      throw new IllegalArgumentException(
          "krizaka.security.jwt.secret must be at least "
              + MINIMUM_SECRET_LENGTH
              + " characters (256-bit HS256)");
    }
    if (jwkSetUri != null && issuer == null) {
      throw new IllegalArgumentException(
          "krizaka.security.jwt.issuer is required with krizaka.security.jwt.jwk-set-uri");
    }
  }

  private static String blankToNull(String value) {
    return value == null || value.isBlank() ? null : value;
  }

  /**
   * Hides the secret from logs, actuator output and exception messages.
   *
   * @return a description that never contains the secret
   */
  @Override
  public String toString() {
    return "SessionJwtProperties[secret=<redacted>, jwkSetUri="
        + jwkSetUri
        + ", issuer="
        + issuer
        + ", audience="
        + audience
        + "]";
  }
}
