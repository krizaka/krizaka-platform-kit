package com.krizaka.webhooks;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.micrometer.observation.ObservationRegistry;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class WebhookVerifierTest {

  private static final Instant NOW = Instant.parse("2026-10-21T12:00:00Z");
  private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
  private static final String BODY = "{\"type\":\"payment.settled\"}";

  private final RecordedObservations observations = new RecordedObservations();

  private WebhookVerifier verifier(String... secrets) {
    return new WebhookVerifier(
        List.of(secrets), Duration.ofMinutes(5), CLOCK, observations.registry());
  }

  private static Map<String, String> signed(String secret, String id, Instant at, String body) {
    return WebhookSignature.headers(secret, null, id, at.getEpochSecond(), body);
  }

  @Test
  void acceptsAValidWebhookAndReturnsItsIdToDedupeOn() {
    assertThat(verifier("s3cret").verify(signed("s3cret", "msg_1", NOW, BODY)::get, BODY))
        .isEqualTo("msg_1");
    assertThat(observations.named(WebhookVerifier.OBSERVATION).getFirst().low())
        .containsEntry("outcome", "valid");
  }

  @Test
  void refusesATamperedBody() {
    var headers = signed("s3cret", "msg_1", NOW, BODY);

    assertThatThrownBy(() -> verifier("s3cret").verify(headers::get, BODY + " "))
        .isInstanceOf(InvalidWebhookException.class)
        .hasMessageContaining("signature");
    assertThat(observations.named(WebhookVerifier.OBSERVATION).getFirst().low())
        .containsEntry("outcome", "invalid");
  }

  @Test
  void refusesAnotherSecret() {
    var headers = signed("other", "msg_1", NOW, BODY);

    assertThatThrownBy(() -> verifier("s3cret").verify(headers::get, BODY))
        .isInstanceOf(InvalidWebhookException.class);
  }

  @Test
  void refusesAReplayedCaptureOutsideTheTolerance() {
    var old = signed("s3cret", "msg_1", NOW.minus(Duration.ofMinutes(6)), BODY);
    var future = signed("s3cret", "msg_1", NOW.plus(Duration.ofMinutes(6)), BODY);

    assertThatThrownBy(() -> verifier("s3cret").verify(old::get, BODY))
        .hasMessageContaining("tolerance");
    assertThatThrownBy(() -> verifier("s3cret").verify(future::get, BODY))
        .hasMessageContaining("tolerance");
  }

  @Test
  void acceptsEdgeOfTheTolerance() {
    var edge = signed("s3cret", "msg_1", NOW.minus(Duration.ofMinutes(5)), BODY);

    assertThat(verifier("s3cret").verify(edge::get, BODY)).isEqualTo("msg_1");
  }

  @Test
  void refusesMissingHeaders() {
    assertThatThrownBy(() -> verifier("s3cret").verify(name -> null, BODY))
        .hasMessageContaining("required");
    var headers = signed("s3cret", "msg_1", NOW, BODY);
    assertThatThrownBy(
            () ->
                verifier("s3cret")
                    .verify(n -> n.equals(WebhookSignature.ID) ? null : headers.get(n), BODY))
        .isInstanceOf(InvalidWebhookException.class);
  }

  @Test
  void refusesANonNumericTimestamp() {
    var headers = signed("s3cret", "msg_1", NOW, BODY);

    assertThatThrownBy(
            () ->
                verifier("s3cret")
                    .verify(
                        n -> n.equals(WebhookSignature.TIMESTAMP) ? "yesterday" : headers.get(n),
                        BODY))
        .hasMessageContaining("number");
  }

  @Test
  void acceptsTheSecretBeingRotatedOut() {
    var fromOld = signed("old", "msg_1", NOW, BODY);

    assertThat(verifier("new", "old").verify(fromOld::get, BODY)).isEqualTo("msg_1");
  }

  @Test
  void acceptsAnyOfTheSignaturesOfARotation() {
    var both = WebhookSignature.headers("new", "old", "msg_1", NOW.getEpochSecond(), BODY);

    assertThat(verifier("old").verify(both::get, BODY)).isEqualTo("msg_1");
    assertThat(verifier("new").verify(both::get, BODY)).isEqualTo("msg_1");
  }

  @Test
  void ignoresSignaturesOfAnotherVersion() {
    var headers = signed("s3cret", "msg_1", NOW, BODY);
    String v2 = headers.get(WebhookSignature.SIGNATURE).replace("v1,", "v2,");

    assertThatThrownBy(
            () ->
                verifier("s3cret")
                    .verify(n -> n.equals(WebhookSignature.SIGNATURE) ? v2 : headers.get(n), BODY))
        .isInstanceOf(InvalidWebhookException.class);
  }

  @Test
  void aNullBodyIsAnEmptyBody() {
    var headers = signed("s3cret", "msg_1", NOW, "");

    assertThat(verifier("s3cret").verify(headers::get, null)).isEqualTo("msg_1");
  }

  @Test
  void theRefusalIsAProblemWithStatus401() {
    var exception = new InvalidWebhookException("why");

    assertThat(exception.status()).isEqualTo(HttpStatus.UNAUTHORIZED);
    assertThat(exception.code()).isEqualTo("invalid-webhook");
  }

  @Test
  void needsAtLeastOneSecret() {
    assertThatThrownBy(
            () ->
                new WebhookVerifier(
                    List.of(), Duration.ofMinutes(5), CLOCK, ObservationRegistry.NOOP))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
