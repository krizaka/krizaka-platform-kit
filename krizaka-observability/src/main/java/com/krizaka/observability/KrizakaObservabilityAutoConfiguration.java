package com.krizaka.observability;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.micrometer.metrics.autoconfigure.MeterRegistryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Binds {@link KrizakaObservabilityProperties} — so a service without its names fails at startup —
 * and, where Micrometer is present, tags every meter with them.
 *
 * <p>The properties are bound unconditionally: having this module on the classpath is the
 * declaration that the service is observed the Krizaka way. The common tags ({@code product},
 * {@code service}, {@code version}) are added only when Spring Boot's Micrometer support is on the
 * classpath; an application without it starts with the names checked and nothing else.
 */
@AutoConfiguration
@EnableConfigurationProperties(KrizakaObservabilityProperties.class)
public class KrizakaObservabilityAutoConfiguration {

  /** The common tag carrying {@link KrizakaObservabilityProperties#product()}. */
  public static final String PRODUCT_TAG = "product";

  /** The common tag carrying {@link KrizakaObservabilityProperties#service()}. */
  public static final String SERVICE_TAG = "service";

  /** The common tag carrying {@link KrizakaObservabilityProperties#version()}. */
  public static final String VERSION_TAG = "version";

  /** Creates the auto-configuration; Spring Boot instantiates it. */
  public KrizakaObservabilityAutoConfiguration() {}

  /** The common tags, only where Spring Boot's Micrometer support is present. */
  @Configuration(proxyBeanMethods = false)
  @ConditionalOnClass({MeterRegistry.class, MeterRegistryCustomizer.class})
  public static class CommonTags {

    /** Creates the configuration; Spring instantiates it. */
    public CommonTags() {}

    /**
     * Adds {@code product}, {@code service} and {@code version} to every meter of every registry.
     *
     * @param properties the service's names
     * @return the customizer Spring Boot applies to each {@link MeterRegistry}
     */
    @Bean
    public MeterRegistryCustomizer<MeterRegistry> krizakaCommonTags(
        KrizakaObservabilityProperties properties) {
      return registry ->
          registry
              .config()
              .commonTags(
                  PRODUCT_TAG,
                  properties.product(),
                  SERVICE_TAG,
                  properties.service(),
                  VERSION_TAG,
                  properties.version());
    }
  }
}
