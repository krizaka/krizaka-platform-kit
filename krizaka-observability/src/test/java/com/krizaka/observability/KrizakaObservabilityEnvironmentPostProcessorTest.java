package com.krizaka.observability;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.PropertiesLoaderUtils;

class KrizakaObservabilityEnvironmentPostProcessorTest {

  private final KrizakaObservabilityEnvironmentPostProcessor postProcessor =
      new KrizakaObservabilityEnvironmentPostProcessor();

  @Test
  void setsTheSharedDefaults() {
    StandardEnvironment environment = new StandardEnvironment();

    postProcessor.postProcessEnvironment(environment, new SpringApplication());

    assertThat(environment.getProperty("logging.structured.format.console")).isEqualTo("ecs");
    assertThat(environment.getProperty("management.endpoints.web.exposure.include"))
        .isEqualTo("health,info,prometheus");
    assertThat(environment.getProperty("management.tracing.sampling.probability")).isEqualTo("0.1");
  }

  @Test
  void everyApplicationValueStillWins() {
    StandardEnvironment environment = new StandardEnvironment();
    environment
        .getPropertySources()
        .addFirst(
            new MapPropertySource(
                "application",
                Map.of(
                    "logging.structured.format.console", "logfmt",
                    "management.tracing.sampling.probability", "1.0")));

    postProcessor.postProcessEnvironment(environment, new SpringApplication());

    assertThat(environment.getProperty("logging.structured.format.console")).isEqualTo("logfmt");
    assertThat(environment.getProperty("management.tracing.sampling.probability")).isEqualTo("1.0");
    assertThat(environment.getPropertySources().stream().toList().getLast().getName())
        .isEqualTo(KrizakaObservabilityEnvironmentPostProcessor.PROPERTY_SOURCE_NAME);
  }

  @Test
  void addsItsSourceOnce() {
    StandardEnvironment environment = new StandardEnvironment();

    postProcessor.postProcessEnvironment(environment, new SpringApplication());
    postProcessor.postProcessEnvironment(environment, new SpringApplication());

    assertThat(
            environment.getPropertySources().stream()
                .filter(
                    source ->
                        source
                            .getName()
                            .equals(
                                KrizakaObservabilityEnvironmentPostProcessor.PROPERTY_SOURCE_NAME))
                .count())
        .isOne();
  }

  @Test
  void isRegisteredWithSpringBoot() throws Exception {
    Properties factories =
        PropertiesLoaderUtils.loadProperties(new ClassPathResource("META-INF/spring.factories"));

    assertThat(factories.getProperty(EnvironmentPostProcessor.class.getName()))
        .isEqualTo(KrizakaObservabilityEnvironmentPostProcessor.class.getName());
  }
}
