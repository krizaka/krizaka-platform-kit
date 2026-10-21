package com.krizaka.webhooks;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class WebhookSenderTest {

  private static final Instant NOW = Instant.parse("2026-10-21T12:00:00Z");
  private static final String BODY = "{\"type\":\"payment.settled\"}";
  private static final String URL = "https://receiver.example/hooks";

  private final RestClient.Builder builder = RestClient.builder();
  private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
  private final RecordedObservations observations = new RecordedObservations();

  private WebhookSender sender(String previous) {
    return new WebhookSender(
        builder.build(),
        "s3cret",
        previous,
        Clock.fixed(NOW, ZoneOffset.UTC),
        observations.registry());
  }

  @Test
  void sendsTheBodyExactlyAsSignedWithTheThreeHeaders() {
    String signature = WebhookSignature.sign("s3cret", "msg_1", NOW.getEpochSecond(), BODY);
    server
        .expect(requestTo(URL))
        .andExpect(method(HttpMethod.POST))
        .andExpect(header("webhook-id", "msg_1"))
        .andExpect(header("webhook-timestamp", Long.toString(NOW.getEpochSecond())))
        .andExpect(header("webhook-signature", signature))
        .andExpect(content().string(BODY))
        .andRespond(withSuccess());

    var attempt = sender(null).send(URL, "msg_1", BODY);

    assertThat(attempt.delivered()).isTrue();
    assertThat(attempt.status()).isEqualTo(200);
    assertThat(observations.named(WebhookSender.OBSERVATION).getFirst().low())
        .containsEntry("outcome", "delivered");
    server.verify();
  }

  @Test
  void signsWithBothSecretsDuringARotation() {
    String both =
        WebhookSignature.headers("s3cret", "old", "msg_1", NOW.getEpochSecond(), BODY)
            .get("webhook-signature");
    server
        .expect(requestTo(URL))
        .andExpect(header("webhook-signature", both))
        .andRespond(withSuccess());

    assertThat(sender("old").send(URL, "msg_1", BODY).delivered()).isTrue();
    server.verify();
  }

  @Test
  void aRefusalIsAnOutcomeNotAnException() {
    server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.BAD_REQUEST));

    var attempt = sender(null).send(URL, "msg_1", BODY);

    assertThat(attempt.delivered()).isFalse();
    assertThat(attempt.status()).isEqualTo(400);
    assertThat(attempt.error()).contains("400");
    assertThat(observations.named(WebhookSender.OBSERVATION).getFirst().low())
        .containsEntry("outcome", "refused");
  }

  @Test
  void aServerErrorIsRefusedToo() {
    server.expect(requestTo(URL)).andRespond(withServerError());

    assertThat(sender(null).send(URL, "msg_1", BODY).status()).isEqualTo(500);
  }

  @Test
  void anUnreachableReceiverIsAnOutcomeNotAnException() {
    server
        .expect(requestTo(URL))
        .andRespond(
            request -> {
              throw new IOException("connection refused");
            });

    var attempt = sender(null).send(URL, "msg_1", BODY);

    assertThat(attempt.delivered()).isFalse();
    assertThat(attempt.status()).isNull();
    assertThat(attempt.error()).startsWith("not delivered");
    assertThat(observations.named(WebhookSender.OBSERVATION).getFirst().low())
        .containsEntry("outcome", "unreachable");
  }

  @Test
  void whatIsSentIsWhatTheVerifierAccepts() {
    var captured =
        new java.util.concurrent.atomic.AtomicReference<org.springframework.http.HttpHeaders>();
    server
        .expect(requestTo(URL))
        .andRespond(
            request -> {
              captured.set(request.getHeaders());
              return withSuccess().createResponse(request);
            });
    sender(null).send(URL, "msg_1", BODY);

    var verifier =
        new WebhookVerifier(
            java.util.List.of("s3cret"),
            java.time.Duration.ofMinutes(5),
            Clock.fixed(NOW, ZoneOffset.UTC),
            io.micrometer.observation.ObservationRegistry.NOOP);
    assertThat(verifier.verify(captured.get()::getFirst, BODY)).isEqualTo("msg_1");
  }
}
