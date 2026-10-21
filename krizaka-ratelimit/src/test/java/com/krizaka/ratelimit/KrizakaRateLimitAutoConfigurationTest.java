package com.krizaka.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

class KrizakaRateLimitAutoConfigurationTest {

  private final WebApplicationContextRunner runner =
      new WebApplicationContextRunner()
          .withConfiguration(AutoConfigurations.of(KrizakaRateLimitAutoConfiguration.class));

  @Test
  void limitsByDefaultWithTheDocumentedDefaults() {
    runner.run(
        context -> {
          assertThat(context)
              .hasSingleBean(RateLimiter.class)
              .hasSingleBean(RateLimitInterceptor.class);
          assertThat(context.getBean(RateLimitKeyResolver.class))
              .isInstanceOf(ClientIpKeyResolver.class);
          var properties = context.getBean(KrizakaRateLimitProperties.class);
          assertThat(properties.mode()).isEqualTo(RateLimitMode.ENFORCE);
          assertThat(properties.requestsPerMinute()).isEqualTo(600);
          assertThat(properties.maxKeys()).isEqualTo(10_000);
          assertThat(properties.excludePaths()).containsExactly("/actuator/**");
          assertThat(properties.includePaths()).containsExactly("/**");
          assertThat(context.getBean(RateLimiter.class).mode()).isEqualTo(RateLimitMode.ENFORCE);
        });
  }

  @Test
  void theModeAndTheLimitAreTheProperties() {
    runner
        .withPropertyValues(
            "krizaka.ratelimit.mode=observe",
            "krizaka.ratelimit.requests-per-minute=30",
            "krizaka.ratelimit.trusted-proxies=10.0.0.0/8,192.168.0.1")
        .run(
            context -> {
              assertThat(context.getBean(RateLimiter.class).mode())
                  .isEqualTo(RateLimitMode.OBSERVE);
              assertThat(context.getBean(KrizakaRateLimitProperties.class).trustedProxies())
                  .containsExactly("10.0.0.0/8", "192.168.0.1");
            });
  }

  @Test
  void offKeepsNothing() {
    runner
        .withPropertyValues("krizaka.ratelimit.mode=off")
        .run(context -> assertThat(context.getBean(RateLimiter.class).active()).isFalse());
  }

  @Test
  void theApplicationsKeyResolverWins() {
    RateLimitKeyResolver own = request -> RateLimitKey.of("mine");
    runner
        .withBean(RateLimitKeyResolver.class, () -> own)
        .run(context -> assertThat(context.getBean(RateLimitKeyResolver.class)).isSameAs(own));
  }

  @Test
  void theInterceptorIsRegisteredWithSpringMvc() {
    runner.run(
        context ->
            assertThat(context)
                .hasBean("krizakaRateLimitMappings")
                .getBean("krizakaRateLimitMappings")
                .isInstanceOf(WebMvcConfigurer.class));
  }

  @Test
  void aWorkerWithoutAWebApplicationIsUntouched() {
    new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(KrizakaRateLimitAutoConfiguration.class))
        .run(context -> assertThat(context).doesNotHaveBean(RateLimiter.class));
  }
}
