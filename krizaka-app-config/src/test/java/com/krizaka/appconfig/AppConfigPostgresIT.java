package com.krizaka.appconfig;

import static org.assertj.core.api.Assertions.assertThat;

import com.krizaka.appconfig.model.AppConfig;
import com.krizaka.appconfig.model.AppConfigChange;
import com.krizaka.appconfig.model.AppConfigUpdate;
import com.krizaka.appconfig.model.AppVersion;
import com.krizaka.appconfig.model.ClientPlatform;
import com.krizaka.appconfig.store.AppConfigStore;
import com.krizaka.test.container.AbstractContainerIntegrationTest;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClient;

/**
 * The whole path against a real PostgreSQL: the shipped schema (seeded at version 1), the public
 * endpoint, the 426 filter reading the database, a publication visible through the API, the audit
 * trail, and two concurrent publications getting distinct revisions.
 */
@SpringBootTest(
    classes = AppConfigPostgresIT.App.class,
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
      "spring.sql.init.mode=always",
      "spring.sql.init.schema-locations=classpath:krizaka-app-config-schema.sql",
      "krizaka.app-config.cache-ttl=0s",
      "spring.autoconfigure.exclude=org.springframework.boot.rabbitmq.autoconfigure.RabbitAutoConfiguration"
    })
class AppConfigPostgresIT extends AbstractContainerIntegrationTest {

  @Value("${local.server.port}")
  int port;

  @Autowired AppConfigStore store;

  private RestClient client() {
    return RestClient.builder()
        .baseUrl("http://localhost:" + port)
        .defaultStatusHandler(HttpStatusCode::isError, (request, response) -> {})
        .build();
  }

  private static AppConfig config(ClientPlatform platform, String min, String latest) {
    return new AppConfigUpdate(
            min,
            latest,
            null,
            "https://play.google.com/store/apps/details?id=com.krizaka.demo",
            false,
            Map.of(),
            Map.of("en", "Update", "fr", "Mettez à jour"),
            1,
            2,
            null,
            Map.of("voice", true),
            "r")
        .toConfig(platform);
  }

  @Test
  void theSchemaSeedsAPermissiveVersionOneForEveryPlatform() {
    assertThat(store.findAll())
        .extracting(c -> c.platform().wire())
        .contains("ios", "android", "macos", "windows", "linux", "web");
    AppConfig seeded = store.find(ClientPlatform.WEB).orElseThrow();
    assertThat(seeded.revision()).isEqualTo(1);
    assertThat(seeded.minSupportedVersion()).isEqualTo(AppVersion.ZERO);
    assertThat(store.history(ClientPlatform.WEB))
        .extracting(AppConfigChange::reason)
        .startsWith("seed");
  }

  @Test
  void aPublishedMinimumRefusesAnOldClientWith426AndTheEndpointSaysSo() {
    store.publish(config(ClientPlatform.ANDROID, "2.0.0", "2.1.0"), "it", "raise the minimum");

    ResponseEntity<String> refused =
        client()
            .get()
            .uri("/api/v1/things")
            .header("X-Client-Platform", "android")
            .header("X-Client-Version", "1.9.0")
            .retrieve()
            .toEntity(String.class);
    assertThat(refused.getStatusCode().value()).isEqualTo(426);
    assertThat(refused.getBody())
        .contains("\"code\":\"upgrade-required\"")
        .contains("\"minSupportedVersion\":\"2.0.0\"");

    ResponseEntity<String> served =
        client()
            .get()
            .uri("/api/v1/things")
            .header("X-Client-Platform", "android")
            .header("X-Client-Version", "2.0.0")
            .retrieve()
            .toEntity(String.class);
    assertThat(served.getStatusCode().value()).isEqualTo(200);

    ResponseEntity<String> config =
        client()
            .get()
            .uri("/api/v1/app-config?platform=android&version=2.0.5&locale=fr")
            .retrieve()
            .toEntity(String.class);
    assertThat(config.getHeaders().getCacheControl()).contains("public");
    assertThat(config.getBody())
        .contains("\"updateMode\":\"soft\"")
        .contains("\"message\":\"Mettez à jour\"")
        .contains("\"flags\":{\"voice\":true}");
  }

  @Test
  void theAuditTrailKeepsEveryRevisionAndRollbackRestoresOne() {
    store.publish(config(ClientPlatform.LINUX, "1.0.0", "1.0.0"), "ana", "first");
    store.publish(config(ClientPlatform.LINUX, "1.5.0", "1.5.0"), "bob", "second");
    AppConfig restored = store.rollback(ClientPlatform.LINUX, 2, "ana", "back").orElseThrow();
    assertThat(restored.minSupportedVersion()).isEqualTo(AppVersion.of("1.0.0"));
    List<AppConfigChange> trail = store.history(ClientPlatform.LINUX);
    assertThat(trail).extracting(change -> change.config().revision()).containsExactly(4, 3, 2, 1);
    assertThat(trail)
        .extracting(AppConfigChange::changedBy)
        .containsExactly("ana", "bob", "ana", "seed");
  }

  @Test
  void twoConcurrentPublicationsGetDistinctRevisions() throws Exception {
    var pool = Executors.newFixedThreadPool(4);
    CountDownLatch go = new CountDownLatch(1);
    List<Future<AppConfig>> results =
        IntStream.range(0, 4)
            .mapToObj(
                i ->
                    pool.submit(
                        () -> {
                          go.await();
                          return store.publish(
                              config(ClientPlatform.WINDOWS, "1.0." + i, "1.0." + i),
                              "t" + i,
                              "race " + i);
                        }))
            .toList();
    go.countDown();
    List<Integer> revisions =
        results.stream()
            .map(
                f -> {
                  try {
                    return f.get().revision();
                  } catch (Exception e) {
                    throw new IllegalStateException(e);
                  }
                })
            .sorted()
            .toList();
    pool.shutdown();
    assertThat(revisions).doesNotHaveDuplicates().hasSize(4);
    assertThat(store.history(ClientPlatform.WINDOWS)).hasSize(5);
  }

  /** The application: auto-configuration and one guarded controller. */
  @SpringBootConfiguration
  @EnableAutoConfiguration
  @org.springframework.context.annotation.Import(Things.class)
  static class App {}

  /** An API endpoint behind the guard. */
  @org.springframework.web.bind.annotation.RestController
  static class Things {
    @org.springframework.web.bind.annotation.GetMapping("/api/v1/things")
    String things() {
      return "[]";
    }
  }
}
