package com.krizaka.security.jwt;

import java.time.Instant;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;
import org.springframework.boot.context.properties.bind.DefaultValue;

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
 * @param jwkSetUri The issuer's JWKS endpoint ('/.well-known/jwks.json'); empty by default, which
 *     leaves this mode off. Only ES256 tokens are accepted in this mode; keys are matched by 'kid',
 *     and an unknown 'kid' triggers a (rate limited) refresh so rotation needs no restart.
 * @param issuer Required with 'jwk-set-uri': the only 'iss' accepted; empty by default.
 * @param audience Optional with 'jwk-set-uri': when set, the 'aud' that must be present; empty by
 *     default.
 * @param hs256Cutoff Optional, with 'secret': from this instant the shared secret verifies nothing
 *     (ISO-8601). Empty keeps it open until the secret is removed.
 * @param callers Optional: the public key (SPKI PEM, P-256) of each calling service by the name it
 *     puts in 'sub'. A token signed by one of these keys, carrying exactly the SERVICE role, is
 *     accepted beside the sessions; a caller cannot speak for another, an unknown name is refused.
 */
@ConfigurationProperties(prefix = "krizaka.security.jwt")
public record SessionJwtProperties(
    String secret,
    @DefaultValue("") String jwkSetUri,
    @DefaultValue("") String issuer,
    @DefaultValue("") String audience,
    Instant hs256Cutoff,
    @DefaultValue Map<String, String> callers) {

  /**
   * The shape before caller keys, kept for binary compatibility.
   *
   * @param secret the legacy shared secret, or null
   * @param jwkSetUri the issuer's JWKS endpoint, or null
   * @param issuer the accepted issuer
   * @param audience the required audience, or null
   * @deprecated use the six-argument form
   */
  @Deprecated
  public SessionJwtProperties(String secret, String jwkSetUri, String issuer, String audience) {
    this(secret, jwkSetUri, issuer, audience, null, null);
  }

  /**
   * The pre-JWKS shape, kept for binary compatibility: the shared HS256 secret alone.
   *
   * @param secret the shared HMAC-SHA256 secret, at least 32 characters
   * @deprecated use the four-argument form; this one only selects the legacy secret mode
   */
  @Deprecated
  public SessionJwtProperties(String secret) {
    this(secret, null, null, null, null, null);
  }

  /** The minimum secret length, in characters, for a 256-bit HS256 key. */
  public static final int MINIMUM_SECRET_LENGTH = 32;

  /**
   * Validates the mode.
   *
   * @throws IllegalArgumentException when both or neither mode is set, the secret is short, or
   *     {@code jwk-set-uri} comes without an {@code issuer}
   */
  @ConstructorBinding
  public SessionJwtProperties {
    secret = blankToNull(secret);
    jwkSetUri = blankToNull(jwkSetUri);
    issuer = blankToNull(issuer);
    audience = blankToNull(audience);
    callers = callers == null ? Map.of() : Map.copyOf(callers);
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
    return "SessionJwtProperties[secret=<redacted>, callers="
        + callers.keySet()
        + ", hs256Cutoff="
        + hs256Cutoff
        + ", jwkSetUri="
        + jwkSetUri
        + ", issuer="
        + issuer
        + ", audience="
        + audience
        + "]";
  }
}
