package com.krizaka.starter.appconfig;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClient;

/**
 * A servlet application with only {@code krizaka-spring-boot-starter-app-config} (store in memory,
 * no database needed): the endpoint answers publicly and an app with a malformed version is not
 * refused by a guard that cannot read it.
 */
@SpringBootTest(
    classes = AppConfigStarterIT.App.class,
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
      "krizaka.app-config.store=memory",
      "spring.autoconfigure.exclude=org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration"
    })
class AppConfigStarterIT {

  @Value("${local.server.port}")
  int port;

  private RestClient client() {
    return RestClient.builder()
        .baseUrl("http://localhost:" + port)
        .defaultStatusHandler(HttpStatusCode::isError, (request, response) -> {})
        .build();
  }

  @Test
  void theEndpointAnswersWithoutAnyConfiguration() {
    ResponseEntity<String> response =
        client()
            .get()
            .uri("/api/v1/app-config?platform=ios&version=1.0.0")
            .retrieve()
            .toEntity(String.class);

    assertThat(response.getStatusCode().value()).isEqualTo(200);
    assertThat(response.getBody())
        .contains("\"platform\":\"ios\"")
        .contains("\"updateMode\":\"none\"")
        .contains("\"contract\":{\"min\":1,\"max\":1,\"sunset\":null}");
  }

  @Test
  void aMalformedVersionIsNotRefusedByTheGuard() {
    ResponseEntity<String> response =
        client()
            .get()
            .uri("/api/v1/app-config?platform=ios")
            .header("X-Client-Platform", "ios")
            .header("X-Client-Version", "not-a-version")
            .retrieve()
            .toEntity(String.class);

    assertThat(response.getStatusCode().value()).isEqualTo(200);
  }

  /** The application: auto-configuration, nothing else. */
  @SpringBootConfiguration
  @EnableAutoConfiguration
  static class App {}
}
