package com.krizaka.security.web;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.krizaka.security.token.ServiceTokenProvider;
import com.krizaka.security.token.ServiceTokenVerifier;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.junit.jupiter.web.SpringJUnitWebConfig;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

/** ADR-001 slice 3: {@code allowed-callers} per internal path, on top of the SERVICE authority. */
@SpringJUnitWebConfig(AllowedCallersTest.Application.class)
class AllowedCallersTest {

  private static final KeyPair BILLING = keyPair();
  private static final KeyPair STUDIO = keyPair();

  private MockMvc mvc;

  @BeforeEach
  void setUp(WebApplicationContext context) {
    mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
  }

  /** Keys are generated here, never committed. */
  private static KeyPair keyPair() {
    try {
      var generator = KeyPairGenerator.getInstance("EC");
      generator.initialize(new ECGenParameterSpec("secp256r1"));
      return generator.generateKeyPair();
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  private static String bearer(KeyPair pair, String caller) {
    return "Bearer "
        + ServiceTokenProvider.es256((ECPrivateKey) pair.getPrivate(), caller + "-1", caller)
            .token();
  }

  @Test
  void anAllowedCallerReachesItsPath() throws Exception {
    mvc.perform(get("/internal/v1/users/1").header("Authorization", bearer(BILLING, "billing")))
        .andExpect(status().isOk());
  }

  @Test
  void aValidCallerNotListedForThePathIsForbidden() throws Exception {
    mvc.perform(get("/internal/v1/users/1").header("Authorization", bearer(STUDIO, "studio")))
        .andExpect(status().isForbidden());
  }

  @Test
  void aPathWithoutAListStaysOpenToAnyServiceToken() throws Exception {
    mvc.perform(get("/internal/v1/other").header("Authorization", bearer(STUDIO, "studio")))
        .andExpect(status().isOk());
  }

  @Test
  void aStrangersTokenNeverReaches() throws Exception {
    mvc.perform(get("/internal/v1/users/1").header("Authorization", bearer(keyPair(), "billing")))
        .andExpect(status().isUnauthorized());
  }

  @Configuration
  @EnableWebMvc
  @EnableWebSecurity
  static class Application {

    @Bean
    JwtDecoder decoder() {
      return new ServiceTokenVerifier(
          Map.of(
              "billing", (ECPublicKey) BILLING.getPublic(),
              "studio", (ECPublicKey) STUDIO.getPublic()));
    }

    @Bean
    SecurityFilterChain chain(HttpSecurity http, JwtDecoder decoder) throws Exception {
      return SecurityBaseline.apply(
              http, Map.of("/internal/v1/users/**", Set.of("billing")), auth -> {})
          .oauth2ResourceServer(
              o -> o.jwt(jwt -> jwt.decoder(decoder).jwtAuthenticationConverter(converter())))
          .build();
    }

    private static JwtAuthenticationConverter converter() {
      var authorities =
          new org.springframework.security.oauth2.server.resource.authentication
              .JwtGrantedAuthoritiesConverter();
      authorities.setAuthoritiesClaimName("roles");
      authorities.setAuthorityPrefix("");
      var converter = new JwtAuthenticationConverter();
      converter.setJwtGrantedAuthoritiesConverter(authorities);
      return converter;
    }

    @RestController
    static class Things {
      @GetMapping("/internal/v1/users/{id}")
      String user() {
        return "ok";
      }

      @GetMapping("/internal/v1/other")
      String other() {
        return "ok";
      }
    }
  }
}
