package com.krizaka.appconfig.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.krizaka.appconfig.model.AppConfigUpdate;
import com.krizaka.appconfig.model.ClientPlatform;
import com.krizaka.appconfig.store.AppConfigReader;
import com.krizaka.appconfig.store.InMemoryAppConfigStore;
import java.io.IOException;
import java.io.InputStream;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * The shared contract: {@code contract/cases.json} is evaluated here and by {@code
 * @krizaka/app-config} (TypeScript); both must agree on every case. A case that cannot pass in one
 * of them is a defect of the contract, fixed in both.
 */
class ContractCasesTest {

  private static final JsonMapper JSON = JsonMapper.builder().build();
  private static final Clock CLOCK =
      Clock.fixed(Instant.parse("2026-10-16T12:00:00Z"), ZoneOffset.UTC);

  static Stream<Arguments> cases() throws IOException {
    try (InputStream in = ContractCasesTest.class.getResourceAsStream("/contract/cases.json")) {
      JsonNode root = JSON.readTree(in);
      return root.get("cases")
          .valueStream()
          .map(node -> Arguments.of(node.get("name").asString(), node));
    }
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("cases")
  @DisplayName("the endpoint answers the fixture")
  void endpointAnswersTheFixture(String name, JsonNode fixture) throws Exception {
    MockMvc mvc = mvcFor(fixture);
    JsonNode client = fixture.get("client");
    MockHttpServletRequestBuilder request =
        get("/api/v1/app-config").param("platform", fixture.get("platform").asString());
    if (client.has("version")) {
      request.param("version", client.get("version").asString());
    }
    if (client.has("language")) {
      request.param("locale", client.get("language").asString());
    }
    MvcResult result = mvc.perform(request).andExpect(status().isOk()).andReturn();
    JsonNode payload = JSON.readTree(result.getResponse().getContentAsString());
    fixture
        .get("expect")
        .get("payload")
        .properties()
        .forEach(
            entry ->
                assertThat(payload.get(entry.getKey()))
                    .as("%s: %s", name, entry.getKey())
                    .isEqualTo(entry.getValue()));
    assertThat(result.getResponse().getHeader(HttpHeaders.CACHE_CONTROL))
        .contains("public", "max-age=60");
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("cases")
  @DisplayName("the filter guards the fixture")
  void filterGuardsTheFixture(String name, JsonNode fixture) throws Exception {
    MockMvc mvc = mvcFor(fixture);
    JsonNode client = fixture.get("client");
    MockHttpServletRequestBuilder request =
        get("/api/v1/things").header(ClientHeaders.PLATFORM, fixture.get("platform").asString());
    if (client.has("version")) {
      request.header(ClientHeaders.VERSION, client.get("version").asString());
    }
    if (client.has("contract")) {
      request.header(ClientHeaders.CONTRACT, client.get("contract").asInt());
    }
    MvcResult result = mvc.perform(request).andReturn();
    JsonNode guard = fixture.get("expect").get("guard");
    assertThat(result.getResponse().getStatus()).as(name).isEqualTo(guard.get("status").asInt());
    if (guard.has("code")) {
      JsonNode problem = JSON.readTree(result.getResponse().getContentAsString());
      assertThat(problem.get("code").asString()).isEqualTo(guard.get("code").asString());
      assertThat(problem.get("status").asInt()).isEqualTo(426);
      assertThat(problem.get("appConfig").get("updateMode").asString()).isEqualTo("force");
      assertThat(result.getResponse().getHeader("Upgrade")).startsWith("krizaka-app/");
      assertThat(result.getResponse().getContentType())
          .startsWith(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
    }
    if (guard.has("deprecation")) {
      assertThat(result.getResponse().getHeader(ClientHeaders.DEPRECATION) != null)
          .as("%s: Deprecation", name)
          .isEqualTo(guard.get("deprecation").asBoolean());
    }
    if (guard.has("sunset")) {
      assertThat(result.getResponse().getHeader(ClientHeaders.SUNSET))
          .isEqualTo(guard.get("sunset").asString());
    }
  }

  private static MockMvc mvcFor(JsonNode fixture) {
    InMemoryAppConfigStore store = new InMemoryAppConfigStore(CLOCK);
    AppConfigUpdate update = JSON.treeToValue(fixture.get("config"), AppConfigUpdate.class);
    ClientPlatform platform =
        ClientPlatform.parse(fixture.get("platform").asString()).orElseThrow();
    store.publish(update.toConfig(platform), "test", "fixture");
    AppConfigReader reader = new AppConfigReader(store, Duration.ZERO, CLOCK);
    ClientVersionFilter filter =
        new ClientVersionFilter(
            reader,
            JSON,
            List.of("/api/**"),
            List.of("/api/v1/app-config"),
            "https://krizaka.com/problems/",
            CLOCK);
    return MockMvcBuilders.standaloneSetup(
            new AppConfigController(reader, JSON, Duration.ofSeconds(60)), new Things())
        .addFilters(filter)
        .build();
  }

  /** An API endpoint behind the guard. */
  @RestController
  static class Things {
    @GetMapping("/api/v1/things")
    String things() {
      return "[]";
    }
  }
}
