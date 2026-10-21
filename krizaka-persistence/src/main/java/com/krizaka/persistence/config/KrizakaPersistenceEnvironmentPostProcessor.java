package com.krizaka.persistence.config;

import java.util.Map;
import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

/**
 * Production defaults for the connection pool, at the lowest priority: the service's own {@code
 * application.yml} still wins over each of them.
 *
 * <ul>
 *   <li>{@code leak-detection-threshold} 20 s — a connection held longer is logged with the stack
 *       that took it, so a leak that would exhaust the pool under load is found before it does (5
 *       s, often suggested, also flags every long report query);
 *   <li>{@code connection-timeout} 10 s — a request waiting for a connection fails in 10 s instead
 *       of Hikari's 30, before the caller's own timeout.
 * </ul>
 */
public class KrizakaPersistenceEnvironmentPostProcessor implements EnvironmentPostProcessor {

  /** The name of the property source holding the defaults. */
  public static final String PROPERTY_SOURCE_NAME = "krizakaPersistenceDefaults";

  /** The defaults. */
  public static final Map<String, Object> DEFAULTS =
      Map.of(
          "spring.datasource.hikari.leak-detection-threshold", "20000",
          "spring.datasource.hikari.connection-timeout", "10000");

  /** Creates the post-processor; Spring Boot instantiates it. */
  public KrizakaPersistenceEnvironmentPostProcessor() {}

  @Override
  public void postProcessEnvironment(
      ConfigurableEnvironment environment, SpringApplication application) {
    if (environment.getPropertySources().contains(PROPERTY_SOURCE_NAME)) {
      return;
    }
    environment.getPropertySources().addLast(new MapPropertySource(PROPERTY_SOURCE_NAME, DEFAULTS));
  }
}
