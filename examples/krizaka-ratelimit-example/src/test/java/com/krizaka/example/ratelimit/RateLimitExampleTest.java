package com.krizaka.example.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClient;

/** The example over HTTP: what a client sees when it goes over its limit. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class RateLimitExampleTest {

  @Value("${local.server.port}")
  int port;

  private ResponseEntity<String> get(String apiKey) {
    return RestClient.builder()
        .baseUrl("http://localhost:" + port)
        .defaultStatusHandler(HttpStatusCode::isError, (request, response) -> {})
        .build()
        .get()
        .uri("/api/v1/quote")
        .headers(h -> h.set("X-Api-Key", apiKey))
        .retrieve()
        .toEntity(String.class);
  }

  @Test
  void theCallOverTheLimitIsA429ProblemWithRetryAfter() {
    String key = UUID.randomUUID().toString();

    for (int i = 0; i < 3; i++) {
      ResponseEntity<String> ok = get(key);
      assertThat(ok.getStatusCode().value()).isEqualTo(200);
      assertThat(ok.getHeaders().getFirst("RateLimit-Limit")).isEqualTo("3");
      assertThat(ok.getHeaders().getFirst("RateLimit-Remaining"))
          .isEqualTo(Integer.toString(2 - i));
    }
    ResponseEntity<String> refused = get(key);

    assertThat(refused.getStatusCode().value()).isEqualTo(429);
    assertThat(refused.getHeaders().getFirst("Retry-After")).isEqualTo("20");
    assertThat(refused.getHeaders().getContentType().toString())
        .contains("application/problem+json");
    assertThat(refused.getBody()).contains("\"code\":\"rate-limit-exceeded\"");
  }

  @Test
  void eachKeyHasItsOwnBucketAndATier() {
    String basic = UUID.randomUUID().toString();
    for (int i = 0; i < 4; i++) {
      get(basic);
    }

    assertThat(get(basic).getStatusCode().value()).isEqualTo(429);
    ResponseEntity<String> pro = get("pro_" + UUID.randomUUID());
    assertThat(pro.getStatusCode().value()).isEqualTo(200);
    assertThat(pro.getHeaders().getFirst("RateLimit-Limit")).isEqualTo("10");
  }
}
