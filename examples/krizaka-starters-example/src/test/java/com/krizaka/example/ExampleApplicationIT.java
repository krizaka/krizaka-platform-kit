package com.krizaka.example;

import static org.assertj.core.api.Assertions.assertThat;

import com.krizaka.security.token.ServiceTokenProvider;
import com.krizaka.test.container.AbstractContainerIntegrationTest;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.client.RestClient;

/**
 * The example service, started on a real PostgreSQL and RabbitMQ with its own application.yml: the
 * four starters and at most 20 lines of YAML make a working service.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ExampleApplicationIT extends AbstractContainerIntegrationTest {

  static final String SECRET = "example-it-secret-of-at-least-32-chars";

  @Value("${local.server.port}")
  int port;

  @DynamicPropertySource
  static void environment(DynamicPropertyRegistry registry) {
    registry.add("DATABASE_URL", () -> postgres().getJdbcUrl());
    registry.add("RABBITMQ_URL", () -> rabbit().getAmqpUrl());
    registry.add("KRIZAKA_JWT_SECRET", () -> SECRET);
  }

  private RestClient client() {
    return RestClient.builder()
        .baseUrl("http://localhost:" + port)
        .defaultStatusHandler(HttpStatusCode::isError, (request, response) -> {})
        .build();
  }

  private String token() {
    return new ServiceTokenProvider(SECRET, "example-it").token();
  }

  @Test
  void theConfigurationFitsInTwentyLines() throws Exception {
    assertThat(Files.readAllLines(Path.of("src/main/resources/application.yml")))
        .hasSizeLessThanOrEqualTo(20);
  }

  @Test
  void healthIsOpenAndUp() {
    ResponseEntity<String> health =
        client().get().uri("/actuator/health").retrieve().toEntity(String.class);

    assertThat(health.getStatusCode().value()).isEqualTo(200);
    assertThat(health.getBody()).contains("\"status\":\"UP\"");
  }

  @Test
  void anAuthenticatedCallerIsGreeted() {
    assertThat(
            client()
                .get()
                .uri("/api/v1/greetings")
                .retrieve()
                .toEntity(String.class)
                .getStatusCode()
                .value())
        .isEqualTo(401);

    ResponseEntity<String> greeting =
        client()
            .get()
            .uri("/api/v1/greetings")
            .headers(h -> h.setBearerAuth(token()))
            .retrieve()
            .toEntity(String.class);

    assertThat(greeting.getStatusCode().value()).isEqualTo(200);
    assertThat(greeting.getBody()).contains("Hello, example-it");
  }

  @Test
  void aDomainErrorIsAProblemDetail() {
    ResponseEntity<String> missing =
        client()
            .get()
            .uri("/api/v1/greetings/7")
            .headers(h -> h.setBearerAuth(token()))
            .retrieve()
            .toEntity(String.class);

    assertThat(missing.getStatusCode().value()).isEqualTo(404);
    assertThat(missing.getBody()).contains("\"code\":\"greeting-not-found\"").contains("requestId");
  }

  @Test
  void metricsCarryTheServiceNames() {
    ResponseEntity<String> metrics =
        client()
            .get()
            .uri("/actuator/prometheus")
            .headers(h -> h.setBearerAuth(token()))
            .retrieve()
            .toEntity(String.class);

    assertThat(metrics.getStatusCode().value()).isEqualTo(200);
    assertThat(metrics.getBody()).contains("product=\"krizaka\"").contains("service=\"example\"");
  }
}
