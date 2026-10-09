package com.krizaka.web;

import java.util.Map;
import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

/**
 * Turns Spring MVC's own Problem Details on ({@code spring.mvc.problemdetails.enabled=true}).
 *
 * <p>The value is added as the <b>lowest-priority</b> property source: every source the application
 * has — {@code application.yaml}, the environment, the command line — still wins, so a service can
 * refuse it. It only matters where {@link com.krizaka.web.problem.ProblemDetailsAdvice} is not
 * active (a service that replaced it, an error outside a controller): the error body is then RFC
 * 9457 as well.
 */
public class KrizakaWebEnvironmentPostProcessor implements EnvironmentPostProcessor {

  /** The name of the property source this post-processor adds. */
  public static final String PROPERTY_SOURCE_NAME = "krizakaWebDefaults";

  /** Creates the post-processor; Spring Boot instantiates it from {@code spring.factories}. */
  public KrizakaWebEnvironmentPostProcessor() {}

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
    environment
        .getPropertySources()
        .addLast(
            new MapPropertySource(
                PROPERTY_SOURCE_NAME, Map.of("spring.mvc.problemdetails.enabled", "true")));
  }
}
