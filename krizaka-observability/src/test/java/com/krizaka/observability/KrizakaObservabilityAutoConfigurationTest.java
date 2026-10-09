package com.krizaka.observability;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.micrometer.metrics.autoconfigure.MeterRegistryCustomizer;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class KrizakaObservabilityAutoConfigurationTest {

  private static final String[] NAMES = {
    "krizaka.observability.product=acme",
    "krizaka.observability.service=orders",
    "krizaka.observability.version=1.2.3"
  };

  private final ApplicationContextRunner runner =
      new ApplicationContextRunner()
          .withConfiguration(AutoConfigurations.of(KrizakaObservabilityAutoConfiguration.class));

  @Test
  void aServiceThatDoesNotNameItselfDoesNotStart() {
    runner.run(
        context ->
            assertThat(context)
                .hasFailed()
                .getFailure()
                .rootCause()
                .hasMessageContaining("krizaka.observability.product")
                .hasMessageContaining("krizaka.observability.service")
                .hasMessageContaining("krizaka.observability.version"));
  }

  @Test
  void aPartialNameIsRefusedWithTheMissingKey() {
    runner
        .withPropertyValues(
            "krizaka.observability.product=acme", "krizaka.observability.service=orders")
        .run(
            context ->
                assertThat(context)
                    .hasFailed()
                    .getFailure()
                    .rootCause()
                    .hasMessageContaining("krizaka.observability.version")
                    .message()
                    .doesNotContain("krizaka.observability.product,"));
  }

  @Test
  @SuppressWarnings("unchecked")
  void tagsEveryMeterWithTheServiceNames() {
    runner
        .withPropertyValues(NAMES)
        .run(
            context -> {
              assertThat(context).hasNotFailed().hasBean("krizakaCommonTags");
              MeterRegistry registry = new SimpleMeterRegistry();
              context.getBean(MeterRegistryCustomizer.class).customize(registry);

              Counter counter = registry.counter("orders.placed");

              assertThat(counter.getId().getTags())
                  .contains(
                      Tag.of("product", "acme"),
                      Tag.of("service", "orders"),
                      Tag.of("version", "1.2.3"));
            });
  }

  @Test
  void anApplicationWithoutMicrometerStartsWithItsNamesChecked() {
    runner
        .withClassLoader(new FilteredClassLoader(MeterRegistry.class))
        .withPropertyValues(NAMES)
        .run(
            context ->
                assertThat(context)
                    .hasNotFailed()
                    .hasSingleBean(KrizakaObservabilityProperties.class)
                    .doesNotHaveBean("krizakaCommonTags"));
  }

  @Test
  void anApplicationWithoutMicrometerStillRefusesToStartUnnamed() {
    runner
        .withClassLoader(new FilteredClassLoader(MeterRegistry.class))
        .run(context -> assertThat(context).hasFailed());
  }
}
