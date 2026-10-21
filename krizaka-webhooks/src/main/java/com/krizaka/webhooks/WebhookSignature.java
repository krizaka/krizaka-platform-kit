package com.krizaka.webhooks;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Signs a webhook the Standard Webhooks way (standardwebhooks.com), so a receiver verifies it with
 * any off-the-shelf library: {@code webhook-signature: v1,<base64 HMAC-SHA256>} over {@code
 * <webhook-id>.<webhook-timestamp>.<body>}.
 *
 * <p>A secret written {@code whsec_<base64>} — the standard's form — is its base64-decoded bytes;
 * any other secret is its UTF-8 bytes (what Orazaka and krizaka-notifications have always signed
 * with). During a rotation both secrets sign, space separated; the receiver accepts either.
 */
public final class WebhookSignature {

  /** The header carrying the message id, stable across the retries of one delivery. */
  public static final String ID = "webhook-id";

  /** The header carrying the attempt's time, in seconds since the epoch. */
  public static final String TIMESTAMP = "webhook-timestamp";

  /** The header carrying the signatures. */
  public static final String SIGNATURE = "webhook-signature";

  /** The standard's prefix of a base64 secret. */
  public static final String SECRET_PREFIX = "whsec_";

  private static final String ALGORITHM = "HmacSHA256";

  private WebhookSignature() {}

  /**
   * {@code v1,<base64>} for one secret.
   *
   * @param secret the secret ({@code whsec_<base64>} or plain text)
   * @param id the message id
   * @param epochSeconds the attempt's time
   * @param body the exact body
   * @return the signature
   */
  public static String sign(String secret, String id, long epochSeconds, String body) {
    try {
      Mac mac = Mac.getInstance(ALGORITHM);
      mac.init(new SecretKeySpec(key(secret), ALGORITHM));
      byte[] digest =
          mac.doFinal((id + "." + epochSeconds + "." + body).getBytes(StandardCharsets.UTF_8));
      return "v1," + Base64.getEncoder().encodeToString(digest);
    } catch (GeneralSecurityException impossible) {
      throw new IllegalStateException("HmacSHA256 is part of every JRE", impossible);
    }
  }

  /**
   * The three headers of an attempt.
   *
   * @param secret the current secret
   * @param previousSecret the secret still honoured after a rotation, or {@code null}
   * @param id the message id
   * @param epochSeconds the attempt's time
   * @param body the exact body
   * @return {@code webhook-id}, {@code webhook-timestamp}, {@code webhook-signature}
   */
  public static Map<String, String> headers(
      String secret, String previousSecret, String id, long epochSeconds, String body) {
    String signature = sign(secret, id, epochSeconds, body);
    if (previousSecret != null && !previousSecret.isBlank()) {
      signature += " " + sign(previousSecret, id, epochSeconds, body);
    }
    Map<String, String> headers = new LinkedHashMap<>();
    headers.put(ID, id);
    headers.put(TIMESTAMP, Long.toString(epochSeconds));
    headers.put(SIGNATURE, signature);
    return headers;
  }

  static byte[] key(String secret) {
    if (secret == null || secret.isEmpty()) {
      throw new IllegalArgumentException("a webhook secret is required");
    }
    if (secret.startsWith(SECRET_PREFIX)) {
      return Base64.getDecoder().decode(secret.substring(SECRET_PREFIX.length()));
    }
    return secret.getBytes(StandardCharsets.UTF_8);
  }
}
