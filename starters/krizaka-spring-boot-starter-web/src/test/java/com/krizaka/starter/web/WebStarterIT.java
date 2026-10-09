package com.krizaka.starter.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.krizaka.web.problem.NotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;

/**
 * A servlet application with only {@code krizaka-spring-boot-starter-web}: Spring MVC runs, the
 * health endpoint answers, errors are Problem Details and the request id travels back.
 */
@SpringBootTest(
    classes = WebStarterIT.App.class,
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class WebStarterIT {

  @Value("${local.server.port}")
  int port;

  private RestClient client() {
    return RestClient.builder()
        .baseUrl("http://localhost:" + port)
        .defaultStatusHandler(HttpStatusCode::isError, (request, response) -> {})
        .build();
  }

  @Test
  void healthIsUp() {
    ResponseEntity<String> health =
        client().get().uri("/actuator/health").retrieve().toEntity(String.class);

    assertThat(health.getStatusCode().value()).isEqualTo(200);
    assertThat(health.getBody()).contains("\"status\":\"UP\"");
  }

  @Test
  void aDomainErrorIsAProblemDetailCarryingTheRequestId() {
    ResponseEntity<String> response =
        client()
            .get()
            .uri("/orders/42")
            .header("X-Request-Id", "starter-web-it")
            .retrieve()
            .toEntity(String.class);

    assertThat(response.getStatusCode().value()).isEqualTo(404);
    assertThat(response.getHeaders().getContentType())
        .isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
    assertThat(response.getHeaders().getFirst("X-Request-Id")).isEqualTo("starter-web-it");
    assertThat(response.getBody())
        .contains("\"code\":\"order-not-found\"")
        .contains("\"requestId\":\"starter-web-it\"");
  }

  /** The application: auto-configuration and one controller, nothing else. */
  @SpringBootConfiguration
  @EnableAutoConfiguration
  @Import(Orders.class)
  static class App {}

  /** A controller whose only answer is a domain error. */
  @RestController
  static class Orders {

    @GetMapping("/orders/{id}")
    String order(@PathVariable String id) {
      throw new NotFoundException("order-not-found", "No order " + id + ".");
    }
  }
}
