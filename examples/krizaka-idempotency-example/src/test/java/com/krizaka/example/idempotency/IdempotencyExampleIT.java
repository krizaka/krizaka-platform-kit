package com.krizaka.example.idempotency;

import static org.assertj.core.api.Assertions.assertThat;

import com.krizaka.test.container.AbstractContainerIntegrationTest;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.client.RestClient;

/** The example on a real PostgreSQL, over HTTP: what a client sees when it retries. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class IdempotencyExampleIT extends AbstractContainerIntegrationTest {

  @Value("${local.server.port}")
  int port;

  @Autowired JdbcTemplate jdbc;

  private ResponseEntity<String> post(String customer, String key, String body) {
    return RestClient.builder()
        .baseUrl("http://localhost:" + port)
        .defaultStatusHandler(HttpStatusCode::isError, (request, response) -> {})
        .build()
        .post()
        .uri("/api/v1/orders")
        .contentType(MediaType.APPLICATION_JSON)
        .headers(
            h -> {
              h.set("X-Customer", customer);
              if (key != null) {
                h.set("Idempotency-Key", key);
              }
            })
        .body(body)
        .retrieve()
        .toEntity(String.class);
  }

  private int orders(String customer) {
    return jdbc.queryForObject(
        "SELECT count(*) FROM orders WHERE customer = ?", Integer.class, customer);
  }

  @Test
  void aRetryWithTheSameKeyReplaysTheOrderAndCreatesNothing() {
    String customer = UUID.randomUUID().toString();
    String key = UUID.randomUUID().toString();

    ResponseEntity<String> first = post(customer, key, "{\"sku\":\"tea\",\"quantity\":2}");
    ResponseEntity<String> retry = post(customer, key, "{\"quantity\":2,\"sku\":\"tea\"}");

    assertThat(first.getStatusCode().value()).isEqualTo(201);
    assertThat(first.getHeaders().getFirst("Idempotent-Replayed")).isNull();
    assertThat(retry.getStatusCode().value()).isEqualTo(201);
    assertThat(retry.getHeaders().getFirst("Idempotent-Replayed")).isEqualTo("true");
    assertThat(retry.getBody()).isEqualTo(first.getBody());
    assertThat(orders(customer)).isEqualTo(1);
  }

  @Test
  void theSameKeyForAnotherOrderIsAProblem() {
    String customer = UUID.randomUUID().toString();
    String key = UUID.randomUUID().toString();
    post(customer, key, "{\"sku\":\"tea\",\"quantity\":2}");

    ResponseEntity<String> reused = post(customer, key, "{\"sku\":\"tea\",\"quantity\":20}");

    assertThat(reused.getStatusCode().value()).isEqualTo(422);
    assertThat(reused.getHeaders().getContentType().toString())
        .contains("application/problem+json");
    assertThat(reused.getBody()).contains("\"code\":\"idempotency-key-reused\"");
    assertThat(orders(customer)).isEqualTo(1);
  }

  @Test
  void aMalformedKeyIsA400Problem() {
    ResponseEntity<String> malformed =
        post(UUID.randomUUID().toString(), "has space", "{\"sku\":\"tea\",\"quantity\":1}");

    assertThat(malformed.getStatusCode().value()).isEqualTo(400);
    assertThat(malformed.getBody()).contains("\"code\":\"invalid-idempotency-key\"");
  }

  @Test
  void withoutAKeyEveryRequestCreates() {
    String customer = UUID.randomUUID().toString();

    post(customer, null, "{\"sku\":\"tea\",\"quantity\":1}");
    post(customer, null, "{\"sku\":\"tea\",\"quantity\":1}");

    assertThat(orders(customer)).isEqualTo(2);
  }
}
