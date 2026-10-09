package com.krizaka.starter.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.krizaka.security.token.ServiceTokenProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;

/**
 * An application with only {@code krizaka-spring-boot-starter-security} and the shared secret: a
 * token minted by another service is verified locally and its roles arrive as authorities.
 */
@SpringBootTest(
    classes = SecurityStarterIT.App.class,
    properties = "krizaka.security.jwt.secret=" + SecurityStarterIT.SECRET)
class SecurityStarterIT {

  static final String SECRET = "starter-security-it-secret-of-32-chars!";

  @Autowired JwtDecoder decoder;
  @Autowired JwtAuthenticationConverter converter;

  @Test
  void aServiceTokenIsVerifiedLocallyWithItsRole() {
    String token = new ServiceTokenProvider(SECRET, "billing-client").token();

    Jwt jwt = decoder.decode(token);

    assertThat(jwt.getSubject()).isEqualTo("billing-client");
    assertThat(converter.convert(jwt).getAuthorities())
        .extracting(GrantedAuthority::getAuthority)
        .contains("SERVICE");
  }

  /** The application: auto-configuration only. */
  @SpringBootConfiguration
  @EnableAutoConfiguration
  static class App {}
}
