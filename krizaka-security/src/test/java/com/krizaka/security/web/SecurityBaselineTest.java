package com.krizaka.security.web;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.krizaka.security.jwt.SessionJwtAutoConfiguration;
import com.krizaka.security.jwt.SessionJwtProperties;
import com.krizaka.security.token.ServiceTokenProvider;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.time.Instant;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
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

/**
 * The baseline, exercised through a real filter chain and real tokens: what is open, what needs a
 * user, and what only a {@code SERVICE} token reaches.
 */
@SpringJUnitWebConfig(SecurityBaselineTest.Application.class)
class SecurityBaselineTest {

  private static final String SECRET = "krizaka-test-secret-at-least-32-characters!";

  @Autowired private WebApplicationContext context;

  private MockMvc mvc;

  @BeforeEach
  void setUp() {
    mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
  }

  @Test
  void healthInfoAndTheErrorPageAreOpen() throws Exception {
    mvc.perform(get("/actuator/health")).andExpect(status().isOk());
    mvc.perform(get("/actuator/info")).andExpect(status().isOk());
    mvc.perform(get("/error")).andExpect(status().isOk());
  }

  @Test
  void aCorsPreflightIsNeverAskedForAToken() throws Exception {
    mvc.perform(options("/api/v1/things")).andExpect(status().isOk());
  }

  @Test
  void anAnonymousCallToTheApiIsRefused() throws Exception {
    mvc.perform(get("/api/v1/things")).andExpect(status().isUnauthorized());
  }

  @Test
  void aUserTokenReachesTheApi() throws Exception {
    mvc.perform(get("/api/v1/things").header("Authorization", "Bearer " + userToken()))
        .andExpect(status().isOk());
  }

  @Test
  void theServiceRulesRunBetweenTheBaselineAndTheCatchAll() throws Exception {
    mvc.perform(get("/api/v1/public/catalogue")).andExpect(status().isOk());
  }

  @Test
  void theInternalSurfaceRefusesAnonymousCalls() throws Exception {
    mvc.perform(get("/internal/v1/things")).andExpect(status().isUnauthorized());
  }

  @Test
  void theInternalSurfaceRefusesAValidUserToken() throws Exception {
    mvc.perform(get("/internal/v1/things").header("Authorization", "Bearer " + userToken()))
        .andExpect(status().isForbidden());
  }

  @Test
  void theInternalSurfaceAcceptsAServiceToken() throws Exception {
    String token = new ServiceTokenProvider(SECRET, "billing-client").token();
    mvc.perform(get("/internal/v1/things").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk());
  }

  /** A session token as identity issues it: {@code roles: ["ROLE_USER"]}, same secret. */
  private static String userToken() {
    long now = Instant.now().getEpochSecond();
    String header = encode("{\"alg\":\"HS256\",\"typ\":\"JWT\"}");
    String payload =
        encode(
            "{\"sub\":\"11111111-1111-4111-8111-111111111111\",\"roles\":[\"ROLE_USER\"],"
                + "\"iat\":%d,\"exp\":%d}".formatted(now, now + 300));
    String signingInput = header + "." + payload;
    try {
      Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
      return signingInput
          + "."
          + Base64.getUrlEncoder()
              .withoutPadding()
              .encodeToString(mac.doFinal(signingInput.getBytes(StandardCharsets.UTF_8)));
    } catch (GeneralSecurityException e) {
      throw new IllegalStateException(e);
    }
  }

  private static String encode(String json) {
    return Base64.getUrlEncoder()
        .withoutPadding()
        .encodeToString(json.getBytes(StandardCharsets.UTF_8));
  }

  @Configuration
  @EnableWebMvc
  @EnableWebSecurity
  static class Application {

    private final SessionJwtAutoConfiguration sessionJwt = new SessionJwtAutoConfiguration();

    @Bean
    JwtDecoder sessionJwtDecoder() {
      return sessionJwt.sessionJwtDecoder(new SessionJwtProperties(SECRET));
    }

    @Bean
    JwtAuthenticationConverter sessionJwtAuthenticationConverter() {
      return sessionJwt.sessionJwtAuthenticationConverter();
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, JwtAuthenticationConverter roles)
        throws Exception {
      return SecurityBaseline.apply(
              http, auth -> auth.requestMatchers("/api/v1/public/**").permitAll())
          .oauth2ResourceServer(o -> o.jwt(jwt -> jwt.jwtAuthenticationConverter(roles)))
          .build();
    }

    @Bean
    Endpoints endpoints() {
      return new Endpoints();
    }
  }

  @RestController
  static class Endpoints {

    @GetMapping({
      "/actuator/health",
      "/actuator/info",
      "/error",
      "/api/v1/things",
      "/api/v1/public/catalogue",
      "/internal/v1/things"
    })
    String ok() {
      return "ok";
    }
  }
}
