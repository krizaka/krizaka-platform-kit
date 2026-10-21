package com.krizaka.persistence.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;

class KrizakaPersistenceEnvironmentPostProcessorTest {

  @Test
  void poolDefaultsAtTheLowestPriority() {
    StandardEnvironment properties = new StandardEnvironment();
    properties
        .getPropertySources()
        .addFirst(
            new MapPropertySource(
                "application", Map.of("spring.datasource.hikari.connection-timeout", "3000")));

    new KrizakaPersistenceEnvironmentPostProcessor()
        .postProcessEnvironment(properties, new SpringApplication());

    assertThat(properties.getProperty("spring.datasource.hikari.leak-detection-threshold"))
        .isEqualTo("20000");
    assertThat(properties.getProperty("spring.datasource.hikari.connection-timeout"))
        .as("the service's own value wins")
        .isEqualTo("3000");
  }
}
