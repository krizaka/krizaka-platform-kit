package com.krizaka.notifications.service.infrastructure.adapter.delivery;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Signs a webhook the way Standard Webhooks does, so a receiver can verify it with any
 * off-the-shelf library: {@code webhook-signature: v1,<base64 HMAC-SHA256>} over {@code
 * "<webhook-id>.<webhook-timestamp>.<body>"}, with the secret as UTF-8 bytes.
 *
 * <p>The signed content binds the message id and its timestamp to the body, so a captured request
 * cannot be replayed under another id or after the receiver's tolerance window.
 */
final class WebhookSigner {

  static final String ID_HEADER = "webhook-id";
  static final String TIMESTAMP_HEADER = "webhook-timestamp";
  static final String SIGNATURE_HEADER = "webhook-signature";

  private static final String ALGORITHM = "HmacSHA256";

  private WebhookSigner() {}

  /** {@code v1,<base64>} for {@code body} sent as message {@code id} at {@code epochSeconds}. */
  static String sign(String secret, String id, long epochSeconds, String body) {
    String content = id + "." + epochSeconds + "." + body;
    try {
      Mac mac = Mac.getInstance(ALGORITHM);
      mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), ALGORITHM));
      byte[] digest = mac.doFinal(content.getBytes(StandardCharsets.UTF_8));
      return "v1," + Base64.getEncoder().encodeToString(digest);
    } catch (GeneralSecurityException impossible) {
      throw new IllegalStateException("HmacSHA256 is required by every JRE", impossible);
    }
  }
}
