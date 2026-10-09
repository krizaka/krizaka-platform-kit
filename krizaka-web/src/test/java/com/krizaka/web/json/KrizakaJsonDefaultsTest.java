package com.krizaka.web.json;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import tools.jackson.databind.json.JsonMapper;

class KrizakaJsonDefaultsTest {

  record Event(String id, String note, Instant at) {}

  private final ApplicationContextRunner runner =
      new ApplicationContextRunner()
          .withConfiguration(AutoConfigurations.of(JacksonAutoConfiguration.class))
          .withBean(KrizakaJsonDefaults.class);

  @Test
  void writesIsoDatesAndOmitsNulls() {
    runner.run(
        context ->
            assertThat(
                    context
                        .getBean(JsonMapper.class)
                        .writeValueAsString(
                            new Event("e-1", null, Instant.parse("2026-10-09T08:00:00Z"))))
                .isEqualTo("{\"id\":\"e-1\",\"at\":\"2026-10-09T08:00:00Z\"}"));
  }

  @Test
  void readsTolerantly() {
    runner.run(
        context ->
            assertThat(
                    context
                        .getBean(JsonMapper.class)
                        .readValue("{\"id\":\"e-1\",\"addedLater\":true}", Event.class))
                .isEqualTo(new Event("e-1", null, null)));
  }

  @Test
  void theApplicationsJacksonPropertiesStillWin() {
    runner
        .withPropertyValues("spring.jackson.default-property-inclusion=always")
        .run(
            context ->
                assertThat(
                        context
                            .getBean(JsonMapper.class)
                            .writeValueAsString(new Event("e-1", null, null)))
                    .contains("\"note\":null"));
  }
}
