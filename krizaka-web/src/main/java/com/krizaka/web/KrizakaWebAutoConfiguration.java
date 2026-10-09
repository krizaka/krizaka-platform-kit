package com.krizaka.web;

import com.krizaka.web.correlation.CorrelationIdFilter;
import com.krizaka.web.cors.KrizakaCors;
import com.krizaka.web.cors.OnCorsOriginsDeclaredCondition;
import com.krizaka.web.json.KrizakaJsonDefaults;
import com.krizaka.web.problem.ProblemDetailsAdvice;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.servlet.DispatcherServlet;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * The HTTP baseline of a Spring MVC service.
 *
 * <p>Active in a servlet web application with Spring MVC; a worker or a batch without them starts
 * untouched. Each bean backs off when the application declares its own:
 *
 * <ul>
 *   <li>{@code krizakaProblemDetailsAdvice} — {@link ProblemDetailsAdvice}, unless the application
 *       has its own {@link ResponseEntityExceptionHandler} (it then answers errors alone; two
 *       advices for the same exceptions would answer in an undefined order);
 *   <li>{@code krizakaCorrelationIdFilter} — {@link CorrelationIdFilter}, first in the chain, so
 *       even a request refused by Spring Security is logged under its id;
 *   <li>{@code krizakaJsonDefaults} — {@link KrizakaJsonDefaults}, when Jackson 3 is present;
 *   <li>{@code corsConfigurationSource} and {@code krizakaCorsMappings} — only when {@code
 *       krizaka.web.cors.allowed-origins} lists an origin. The bean name is the one Spring
 *       Security's {@code cors()} looks up, and the same policy is registered with Spring MVC, so a
 *       service answers preflights identically with or without Spring Security.
 * </ul>
 *
 * <p>It runs before Spring Boot's MVC auto-configuration, whose own Problem Details handler then
 * backs off in favour of {@link ProblemDetailsAdvice}.
 */
@AutoConfiguration(
    beforeName = "org.springframework.boot.webmvc.autoconfigure.WebMvcAutoConfiguration")
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnClass(DispatcherServlet.class)
@EnableConfigurationProperties(KrizakaWebProperties.class)
public class KrizakaWebAutoConfiguration {

  /** Creates the auto-configuration. */
  public KrizakaWebAutoConfiguration() {}

  /**
   * One error format: RFC 9457 Problem Details with {@code code} and {@code requestId}.
   *
   * @param properties the problem base type
   * @return the advice
   */
  @Bean
  @ConditionalOnMissingBean(ResponseEntityExceptionHandler.class)
  public ProblemDetailsAdvice krizakaProblemDetailsAdvice(KrizakaWebProperties properties) {
    return new ProblemDetailsAdvice(properties);
  }

  /**
   * {@code X-Request-Id} in, out and in the MDC, before any other filter.
   *
   * @return the registration, at {@link Ordered#HIGHEST_PRECEDENCE}
   */
  @Bean
  @ConditionalOnMissingBean(name = "krizakaCorrelationIdFilter")
  public FilterRegistrationBean<CorrelationIdFilter> krizakaCorrelationIdFilter() {
    FilterRegistrationBean<CorrelationIdFilter> registration =
        new FilterRegistrationBean<>(new CorrelationIdFilter());
    registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
    return registration;
  }

  /** Jackson 3 defaults, when Spring Boot's Jackson support is on the classpath. */
  @Configuration(proxyBeanMethods = false)
  @ConditionalOnClass(
      name = "org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer")
  static class JsonDefaults {

    @Bean
    @ConditionalOnMissingBean(name = "krizakaJsonDefaults")
    KrizakaJsonDefaults krizakaJsonDefaults() {
      return new KrizakaJsonDefaults();
    }
  }

  /** CORS for the declared origins only. */
  @Configuration(proxyBeanMethods = false)
  @Conditional(OnCorsOriginsDeclaredCondition.class)
  static class Cors {

    @Bean
    @ConditionalOnMissingBean(name = "corsConfigurationSource")
    UrlBasedCorsConfigurationSource corsConfigurationSource(KrizakaWebProperties properties) {
      return KrizakaCors.source(properties.cors().allowedOrigins());
    }

    @Bean
    WebMvcConfigurer krizakaCorsMappings(KrizakaWebProperties properties) {
      CorsConfiguration policy = KrizakaCors.configuration(properties.cors().allowedOrigins());
      return new WebMvcConfigurer() {
        @Override
        public void addCorsMappings(CorsRegistry registry) {
          registry.addMapping("/**").combine(policy);
        }
      };
    }
  }
}
