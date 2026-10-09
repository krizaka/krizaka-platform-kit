package com.krizaka.observability;

import java.util.Map;
import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

/**
 * The observability defaults every Krizaka service shares, at the <b>lowest priority</b>.
 *
 * <ul>
 *   <li>{@code logging.structured.format.console=ecs} — one JSON line per event, the format a
 *       single log parser reads for every service;
 *   <li>{@code management.endpoints.web.exposure.include=health,info,prometheus} — what a platform
 *       scrapes and probes, and nothing else;
 *   <li>{@code management.tracing.sampling.probability=0.1} — one request in ten traced.
 * </ul>
 *
 * <p>The values are added as the last property source: {@code application.yaml}, the environment
 * and the command line still win, so a service (or a developer who wants plain console logs) can
 * override each of them.
 */
public class KrizakaObservabilityEnvironmentPostProcessor implements EnvironmentPostProcessor {

  /** The name of the property source this post-processor adds. */
  public static final String PROPERTY_SOURCE_NAME = "krizakaObservabilityDefaults";

  /** The defaults, by property name. */
  public static final Map<String, Object> DEFAULTS =
      Map.of(
          "logging.structured.format.console", "ecs",
          "management.endpoints.web.exposure.include", "health,info,prometheus",
          "management.tracing.sampling.probability", "0.1");

  /** Creates the post-processor; Spring Boot instantiates it from {@code spring.factories}. */
  public KrizakaObservabilityEnvironmentPostProcessor() {}

  /**
   * Adds the defaults below every other property source.
   *
   * @param environment the application's environment
   * @param application the application being started
   */
  @Override
  public void postProcessEnvironment(
      ConfigurableEnvironment environment, SpringApplication application) {
    if (environment.getPropertySources().contains(PROPERTY_SOURCE_NAME)) {
      return;
    }
    environment.getPropertySources().addLast(new MapPropertySource(PROPERTY_SOURCE_NAME, DEFAULTS));
  }
}
