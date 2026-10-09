package com.krizaka.web;

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

class KrizakaWebEnvironmentPostProcessorTest {

  private final KrizakaWebEnvironmentPostProcessor postProcessor =
      new KrizakaWebEnvironmentPostProcessor();

  @Test
  void turnsSpringMvcProblemDetailsOn() {
    StandardEnvironment environment = new StandardEnvironment();

    postProcessor.postProcessEnvironment(environment, new SpringApplication());

    assertThat(environment.getProperty("spring.mvc.problemdetails.enabled")).isEqualTo("true");
  }

  @Test
  void theApplicationCanStillRefuseIt() {
    StandardEnvironment environment = new StandardEnvironment();
    environment
        .getPropertySources()
        .addFirst(
            new MapPropertySource(
                "application", Map.of("spring.mvc.problemdetails.enabled", "false")));

    postProcessor.postProcessEnvironment(environment, new SpringApplication());

    assertThat(environment.getProperty("spring.mvc.problemdetails.enabled")).isEqualTo("false");
    assertThat(environment.getPropertySources().stream().toList().getLast().getName())
        .isEqualTo(KrizakaWebEnvironmentPostProcessor.PROPERTY_SOURCE_NAME);
  }

  @Test
  void isRegisteredWithSpringBoot() throws Exception {
    Properties factories =
        PropertiesLoaderUtils.loadProperties(new ClassPathResource("META-INF/spring.factories"));

    assertThat(factories.getProperty(EnvironmentPostProcessor.class.getName()))
        .isEqualTo(KrizakaWebEnvironmentPostProcessor.class.getName());
  }
}
