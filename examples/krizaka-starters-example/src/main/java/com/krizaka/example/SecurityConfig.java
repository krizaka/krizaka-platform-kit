package com.krizaka.example;

import com.krizaka.security.web.SecurityBaseline;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;

/** The one filter chain: the Krizaka baseline, session tokens verified locally. */
@Configuration(proxyBeanMethods = false)
public class SecurityConfig {

  /** Spring instantiates it. */
  public SecurityConfig() {}

  /**
   * Health and info open, everything else authenticated by a session or service token.
   *
   * @param http the security builder
   * @param roles the roles-claim converter of krizaka-security
   * @return the chain
   * @throws Exception when Spring Security refuses the configuration
   */
  @Bean
  public SecurityFilterChain securityFilterChain(
      HttpSecurity http, JwtAuthenticationConverter roles) throws Exception {
    return SecurityBaseline.apply(http, auth -> {})
        .oauth2ResourceServer(o -> o.jwt(jwt -> jwt.jwtAuthenticationConverter(roles)))
        .build();
  }
}
