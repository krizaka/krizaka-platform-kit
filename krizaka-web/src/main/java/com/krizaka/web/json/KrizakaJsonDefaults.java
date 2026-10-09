package com.krizaka.web.json;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.core.Ordered;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.cfg.DateTimeFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * The JSON every Krizaka service writes and reads (Jackson 3): ISO-8601 dates and durations, {@code
 * null} fields omitted, unknown fields ignored — the tolerant reader that lets a producer add a
 * field without breaking its consumers.
 *
 * <p>Applied <b>before</b> Spring Boot's own customizer, so any {@code spring.jackson.*} property
 * the application sets still wins.
 */
public final class KrizakaJsonDefaults implements JsonMapperBuilderCustomizer, Ordered {

  /** Creates the customizer. */
  public KrizakaJsonDefaults() {}

  /**
   * Applies the defaults.
   *
   * @param builder the mapper being built
   */
  @Override
  public void customize(JsonMapper.Builder builder) {
    builder
        .changeDefaultPropertyInclusion(
            inclusion -> inclusion.withValueInclusion(JsonInclude.Include.NON_NULL))
        .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
        .disable(DateTimeFeature.WRITE_DATES_AS_TIMESTAMPS)
        .disable(DateTimeFeature.WRITE_DURATIONS_AS_TIMESTAMPS);
  }

  /**
   * Runs before Spring Boot's standard customizer (order {@code 0}).
   *
   * @return {@code -100}
   */
  @Override
  public int getOrder() {
    return -100;
  }
}
