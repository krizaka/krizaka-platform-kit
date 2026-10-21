package com.krizaka.example.webhooks;

import static org.assertj.core.api.Assertions.assertThat;

import com.krizaka.webhooks.WebhookSender;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClient;

/** The example over HTTP: the kit's sender calls the kit's receiver, and a forgery is refused. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class WebhooksExampleTest {

  private static final String BODY = "{\"type\":\"payment.settled\",\"amount\":4200}";

  @Value("${local.server.port}")
  int port;

  @Autowired WebhookSender sender;

  private String url() {
    return "http://localhost:" + port + "/webhooks/payments";
  }

  private ResponseEntity<String> post(String id, String timestamp, String signature) {
    return RestClient.builder()
        .defaultStatusHandler(HttpStatusCode::isError, (request, response) -> {})
        .build()
        .post()
        .uri(url())
        .contentType(MediaType.APPLICATION_JSON)
        .headers(
            h -> {
              h.set("webhook-id", id);
              h.set("webhook-timestamp", timestamp);
              h.set("webhook-signature", signature);
            })
        .body(BODY)
        .retrieve()
        .toEntity(String.class);
  }

  @Test
  void aSignedWebhookIsAccepted() {
    String id = "msg_" + UUID.randomUUID();

    var attempt = sender.send(url(), id, BODY);

    assertThat(attempt.delivered()).isTrue();
    assertThat(attempt.status()).isEqualTo(204);
    assertThat(
            RestClient.create()
                .get()
                .uri("http://localhost:" + port + "/api/v1/settled")
                .retrieve()
                .body(String.class))
        .contains(id);
  }

  @Test
  void aForgeryIsAProblemWithStatus401() {
    ResponseEntity<String> forged =
        post(
            "msg_" + UUID.randomUUID(),
            Long.toString(System.currentTimeMillis() / 1000),
            "v1,AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=");

    assertThat(forged.getStatusCode().value()).isEqualTo(401);
    assertThat(forged.getHeaders().getContentType().toString())
        .contains("application/problem+json");
    assertThat(forged.getBody()).contains("\"code\":\"invalid-webhook\"");
  }

  @Test
  void anUnsignedRequestIsRefused() {
    var unsigned =
        RestClient.builder()
            .defaultStatusHandler(HttpStatusCode::isError, (request, response) -> {})
            .build()
            .post()
            .uri(url())
            .contentType(MediaType.APPLICATION_JSON)
            .body(BODY)
            .retrieve()
            .toBodilessEntity();

    assertThat(unsigned.getStatusCode().value()).isEqualTo(401);
  }
}
