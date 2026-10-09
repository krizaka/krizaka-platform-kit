package com.krizaka.starter.observability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry;
import io.micrometer.tracing.Tracer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;

/**
 * An application with only {@code krizaka-spring-boot-starter-observability} and its three names:
 * metrics are tagged and scraped as Prometheus text, a tracer exists, logs are ECS JSON — and the
 * same application without its names does not start.
 */
@SpringBootTest(
    classes = ObservabilityStarterIT.App.class,
    properties = {
      "krizaka.observability.product=acme",
      "krizaka.observability.service=orders",
      "krizaka.observability.version=1.2.3"
    })
class ObservabilityStarterIT {

  @Autowired MeterRegistry registry;
  @Autowired PrometheusMeterRegistry prometheus;
  @Autowired Tracer tracer;
  @Autowired Environment environment;

  @Test
  void everyMeterCarriesTheServiceNames() {
    registry.counter("orders.placed").increment();

    assertThat(registry.get("orders.placed").counter().getId().getTags())
        .contains(
            Tag.of("product", "acme"), Tag.of("service", "orders"), Tag.of("version", "1.2.3"));
    assertThat(prometheus.scrape())
        .contains("orders_placed_total{product=\"acme\",service=\"orders\",version=\"1.2.3\"}");
  }

  @Test
  void tracingAndTheSharedDefaultsAreOn() {
    assertThat(tracer.nextSpan()).isNotNull();
    assertThat(environment.getProperty("logging.structured.format.console")).isEqualTo("ecs");
    assertThat(environment.getProperty("management.endpoints.web.exposure.include"))
        .isEqualTo("health,info,prometheus");
    assertThat(environment.getProperty("management.tracing.sampling.probability")).isEqualTo("0.1");
  }

  @Test
  void anUnnamedServiceDoesNotStart() {
    assertThatThrownBy(
            () ->
                new SpringApplicationBuilder(App.class)
                    .web(WebApplicationType.NONE)
                    .properties("krizaka.observability.product=acme")
                    .run()
                    .close())
        .rootCause()
        .hasMessageContaining("krizaka.observability.service")
        .hasMessageContaining("krizaka.observability.version");
  }

  /** The application: auto-configuration only. */
  @SpringBootConfiguration
  @EnableAutoConfiguration
  static class App {}
}
