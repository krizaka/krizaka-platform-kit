package com.krizaka.security.token;

import com.krizaka.security.web.SecurityBaseline;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.Signature;
import java.security.interfaces.ECPrivateKey;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Objects;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Mints the machine-to-machine token that authenticates a call to another service's {@code
 * /internal/v1/**}.
 *
 * <p>The token carries {@code roles: ["SERVICE"]}. Services map the {@code roles} claim to
 * authorities with no prefix, so the authority a matcher sees is the raw string {@code SERVICE} —
 * not {@code SCOPE_SERVICE}, not {@code ROLE_SERVICE}.
 *
 * <p>Since ADR-001 a caller signs with its own ES256 key ({@link #es256}); the shared-secret
 * constructor is the legacy path, kept until the services migrate (slice 4). Legacy: it is signed
 * with the shared HS256 session secret, because that is the trust root every validator already
 * holds. It follows that <b>any process holding the secret can mint {@code SERVICE}</b>: the
 * boundary is the secret, not the caller. Keep the secret out of anything that does not need to
 * call an internal surface.
 *
 * <p>Signing is an HMAC and a base64url join, written over {@code javax.crypto} so a thin HTTP
 * client does not pull in a JOSE library; verification — where the subtlety lives — stays with
 * Nimbus on the receiving side. A token is minted per call rather than cached: it lives five
 * minutes, an HMAC costs microseconds, and a cache would need invalidation logic whose only purpose
 * is to save that.
 *
 * <p>Thread-safe.
 */
public final class ServiceTokenProvider {

  /** How long a minted token is valid: short, so a leaked token expires before it is useful. */
  public static final Duration LIFETIME = Duration.ofMinutes(5);

  private static final String HEADER = "{\"alg\":\"HS256\",\"typ\":\"JWT\"}";
  private static final String HMAC_SHA256 = "HmacSHA256";
  private static final int MINIMUM_SECRET_LENGTH = 32;

  private static final String ES256_HEADER = "{\"alg\":\"ES256\",\"typ\":\"JWT\",\"kid\":\"%s\"}";

  private final byte[] secret;
  private final ECPrivateKey privateKey;
  private final String keyId;
  private final String subject;
  private final Clock clock;

  /**
   * A provider signing with {@code secret} on behalf of {@code subject}.
   *
   * @param secret the shared HS256 secret, at least 32 characters
   * @param subject who is calling — the {@code sub} claim, e.g. the calling service's name
   */
  public ServiceTokenProvider(String secret, String subject) {
    this(secret, subject, Clock.systemUTC());
  }

  /**
   * A provider with an explicit clock, for tests.
   *
   * @param secret the shared HS256 secret, at least 32 characters
   * @param subject who is calling
   * @param clock the source of {@code iat} and {@code exp}
   */
  public ServiceTokenProvider(String secret, String subject, Clock clock) {
    Objects.requireNonNull(secret, "the shared secret is required to call /internal/v1");
    if (secret.length() < MINIMUM_SECRET_LENGTH) {
      throw new IllegalArgumentException(
          "the HS256 secret must be at least " + MINIMUM_SECRET_LENGTH + " characters");
    }
    if (subject == null || subject.isBlank() || subject.contains("\"")) {
      throw new IllegalArgumentException("subject must be a non-blank name without quotes");
    }
    this.secret = secret.getBytes(StandardCharsets.UTF_8);
    this.privateKey = null;
    this.keyId = null;
    this.subject = subject;
    this.clock = Objects.requireNonNull(clock, "clock");
  }

  private ServiceTokenProvider(ECPrivateKey key, String keyId, String subject, Clock clock) {
    Objects.requireNonNull(key, "the caller's private key is required to call /internal/v1");
    if (keyId == null || keyId.isBlank() || keyId.contains("\"")) {
      throw new IllegalArgumentException("keyId must be a non-blank name without quotes");
    }
    if (subject == null || subject.isBlank() || subject.contains("\"")) {
      throw new IllegalArgumentException("subject must be a non-blank name without quotes");
    }
    this.secret = null;
    this.privateKey = key;
    this.keyId = keyId;
    this.subject = subject;
    this.clock = Objects.requireNonNull(clock, "clock");
  }

  /**
   * A provider signing ES256 with the caller's own private key (ADR-001): the verifiers hold only
   * its public half, so no shared secret can mint a {@code SERVICE} token for this caller.
   *
   * @param key this caller's private key (P-256), read from its environment, never committed
   * @param keyId the {@code kid} to put in the header
   * @param subject who is calling — the caller's registered name
   * @return the provider
   */
  public static ServiceTokenProvider es256(ECPrivateKey key, String keyId, String subject) {
    return es256(key, keyId, subject, Clock.systemUTC());
  }

  /**
   * As {@link #es256(ECPrivateKey, String, String)}, with an explicit clock for tests.
   *
   * @param key this caller's private key (P-256)
   * @param keyId the {@code kid} to put in the header
   * @param subject who is calling
   * @param clock the source of {@code iat} and {@code exp}
   * @return the provider
   */
  public static ServiceTokenProvider es256(
      ECPrivateKey key, String keyId, String subject, Clock clock) {
    return new ServiceTokenProvider(key, keyId, subject, clock);
  }

  /**
   * A freshly signed service token.
   *
   * @return the compact JWS, ready for an {@code Authorization: Bearer} header
   */
  public String token() {
    Instant now = clock.instant();
    String payload =
        "{\"sub\":\"%s\",\"roles\":[\"%s\"],\"iat\":%d,\"exp\":%d}"
            .formatted(
                subject,
                SecurityBaseline.SERVICE_AUTHORITY,
                now.getEpochSecond(),
                now.plus(LIFETIME).getEpochSecond());
    String signingInput =
        encode(header().getBytes(StandardCharsets.UTF_8))
            + "."
            + encode(payload.getBytes(StandardCharsets.UTF_8));
    return signingInput + "." + encode(sign(signingInput));
  }

  private String header() {
    return privateKey == null ? HEADER : ES256_HEADER.formatted(keyId);
  }

  private byte[] sign(String signingInput) {
    if (privateKey != null) {
      return signEs256(signingInput);
    }
    try {
      Mac mac = Mac.getInstance(HMAC_SHA256);
      mac.init(new SecretKeySpec(secret, HMAC_SHA256));
      return mac.doFinal(signingInput.getBytes(StandardCharsets.UTF_8));
    } catch (GeneralSecurityException e) {
      throw new IllegalStateException("cannot sign the service token", e);
    }
  }

  private byte[] signEs256(String signingInput) {
    try {
      // P1363 = the raw r||s a JWS carries (RFC 7518 3.4), not the DER form.
      Signature signature = Signature.getInstance("SHA256withECDSAinP1363Format");
      signature.initSign(privateKey);
      signature.update(signingInput.getBytes(StandardCharsets.UTF_8));
      return signature.sign();
    } catch (GeneralSecurityException e) {
      throw new IllegalStateException("cannot sign the service token", e);
    }
  }

  private static String encode(byte[] value) {
    return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
  }
}
