package com.krizaka.security.jwt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;

class SessionJwtAutoConfigurationTest {

  private static final String SECRET = "krizaka-test-secret-at-least-32-characters!";

  private final ApplicationContextRunner runner =
      new ApplicationContextRunner()
          .withConfiguration(AutoConfigurations.of(SessionJwtAutoConfiguration.class));

  @Test
  void staysOutOfTheWayWithoutASecret() {
    runner.run(
        context ->
            assertThat(context)
                .doesNotHaveBean(JwtDecoder.class)
                .doesNotHaveBean(JwtAuthenticationConverter.class));
  }

  @Test
  void contributesTheDecoderAndTheRolesConverterWhenTheSecretIsSet() {
    runner
        .withPropertyValues("krizaka.security.jwt.secret=" + SECRET)
        .run(
            context ->
                assertThat(context)
                    .hasSingleBean(JwtDecoder.class)
                    .hasSingleBean(JwtAuthenticationConverter.class)
                    .hasBean("sessionJwtDecoder"));
  }

  @Test
  void refusesToStartWithAShortSecret() {
    runner
        .withPropertyValues("krizaka.security.jwt.secret=too-short")
        .run(context -> assertThat(context).hasFailed());
  }

  @Test
  void backsOffWhenTheApplicationDeclaresItsOwnDecoder() {
    JwtDecoder own = mock(JwtDecoder.class);
    runner
        .withPropertyValues("krizaka.security.jwt.secret=" + SECRET)
        .withBean("ownDecoder", JwtDecoder.class, () -> own)
        .run(context -> assertThat(context.getBean(JwtDecoder.class)).isSameAs(own));
  }

  @Test
  void anApplicationWithoutSpringSecurityStartsWithoutTheDecoder() {
    runner
        .withClassLoader(
            new FilteredClassLoader(JwtDecoder.class, JwtAuthenticationConverter.class))
        .withPropertyValues("krizaka.security.jwt.secret=" + SECRET)
        .run(context -> assertThat(context).hasNotFailed().doesNotHaveBean("sessionJwtDecoder"));
  }
}
