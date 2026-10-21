package com.krizaka.webhooks;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class WebhookSignatureTest {

  /** The test vector of the Standard Webhooks specification. */
  private static final String SECRET = "whsec_MfKQ9r8GKYqrTwjUPD8ILPZIo2LaLaSw";

  @Test
  void matchesTheStandardsOwnTestVector() {
    assertThat(
            WebhookSignature.sign(
                SECRET, "msg_p5jXN8AQM9LWM0D4loKWxJek", 1614265330L, "{\"test\": 2432232314}"))
        .isEqualTo("v1,g0hM9SsE+OTPJTGt/tmIKtSyZlE3uFJELVlNIOLJ1OE=");
  }

  @Test
  void aPlainSecretSignsWithItsUtf8Bytes() {
    assertThat(WebhookSignature.sign("hunter2", "msg_1", 10L, "{}"))
        .startsWith("v1,")
        .isNotEqualTo(WebhookSignature.sign("hunter3", "msg_1", 10L, "{}"));
  }

  @Test
  void bothSecretsSignDuringARotation() {
    var headers = WebhookSignature.headers("new", "old", "msg_1", 10L, "{}");

    assertThat(headers.keySet())
        .containsExactly(
            WebhookSignature.ID, WebhookSignature.TIMESTAMP, WebhookSignature.SIGNATURE);
    assertThat(headers.get(WebhookSignature.TIMESTAMP)).isEqualTo("10");
    assertThat(headers.get(WebhookSignature.SIGNATURE).split(" "))
        .containsExactly(
            WebhookSignature.sign("new", "msg_1", 10L, "{}"),
            WebhookSignature.sign("old", "msg_1", 10L, "{}"));
  }

  @Test
  void aSingleSecretSignsOnce() {
    assertThat(WebhookSignature.headers("new", null, "msg_1", 10L, "{}").get("webhook-signature"))
        .doesNotContain(" ");
  }

  @Test
  void aMissingSecretIsRefused() {
    assertThatThrownBy(() -> WebhookSignature.key("")).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> WebhookSignature.key(null))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
