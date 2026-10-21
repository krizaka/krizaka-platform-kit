package com.krizaka.ratelimit;

import io.micrometer.observation.ObservationRegistry;
import java.time.Clock;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.web.servlet.DispatcherServlet;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * The limit per key of a Spring MVC service: a {@link RateLimiter}, the default {@link
 * ClientIpKeyResolver} (replace it with a {@link RateLimitKeyResolver} bean) and the interceptor
 * that refuses with {@code 429}. Each bean backs off when the application declares its own; {@code
 * krizaka.ratelimit.mode: off} keeps nothing.
 */
@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnClass(DispatcherServlet.class)
@EnableConfigurationProperties(KrizakaRateLimitProperties.class)
public class KrizakaRateLimitAutoConfiguration {

  /** Creates the auto-configuration. */
  public KrizakaRateLimitAutoConfiguration() {}

  /**
   * The limiter.
   *
   * @param properties the mode and the limits
   * @return the limiter
   */
  @Bean
  @ConditionalOnMissingBean
  public RateLimiter rateLimiter(KrizakaRateLimitProperties properties) {
    return new RateLimiter(
        properties.mode(), properties.requestsPerMinute(), properties.maxKeys(), Clock.systemUTC());
  }

  /**
   * The default key: the client's address.
   *
   * @param properties the trusted proxies
   * @return the resolver
   */
  @Bean
  @ConditionalOnMissingBean(RateLimitKeyResolver.class)
  public RateLimitKeyResolver rateLimitKeyResolver(KrizakaRateLimitProperties properties) {
    return new ClientIpKeyResolver(properties.trustedProxies(), properties.clientIpHeader());
  }

  /**
   * The interceptor.
   *
   * @param limiter the limiter
   * @param keys who a request is
   * @param observations where requests are observed, when Micrometer is configured
   * @return the interceptor
   */
  @Bean
  @ConditionalOnMissingBean
  public RateLimitInterceptor rateLimitInterceptor(
      RateLimiter limiter,
      RateLimitKeyResolver keys,
      ObjectProvider<ObservationRegistry> observations) {
    return new RateLimitInterceptor(
        limiter, keys, observations.getIfAvailable(() -> ObservationRegistry.NOOP));
  }

  /**
   * Puts the interceptor on the configured paths.
   *
   * @param interceptor the interceptor
   * @param properties the paths
   * @return the MVC configuration
   */
  @Bean
  public WebMvcConfigurer krizakaRateLimitMappings(
      RateLimitInterceptor interceptor, KrizakaRateLimitProperties properties) {
    return new WebMvcConfigurer() {
      @Override
      public void addInterceptors(InterceptorRegistry registry) {
        registry
            .addInterceptor(interceptor)
            .addPathPatterns(properties.includePaths())
            .excludePathPatterns(properties.excludePaths());
      }
    };
  }
}
