package com.krizaka.security.jwt;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.AnyNestedCondition;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;

/**
 * Local verification of Krizaka session tokens.
 *
 * <p>Active when {@code krizaka.security.jwt.jwk-set-uri} (issuer keys, ES256) or the legacy {@code
 * krizaka.security.jwt.secret} (shared HS256) is set; both together fail at startup. It contributes
 * two beans, each backing off when the application declares its own:
 *
 * <ul>
 *   <li>{@code sessionJwtDecoder} — a Nimbus decoder that accepts only the issuer's ES256 tokens
 *       (JWKS mode) or HS256 tokens signed with the shared secret (legacy mode);
 *   <li>{@code sessionJwtAuthenticationConverter} — maps the {@code roles} claim straight onto
 *       authorities, with <b>no prefix</b>. Identity already emits {@code ROLE_USER}, {@code
 *       ROLE_ADMIN} and {@code SERVICE}; a converter that added {@code SCOPE_} would make a correct
 *       token fail every matcher, and the tempting "fix" is to weaken the matcher.
 * </ul>
 */
@AutoConfiguration
@ConditionalOnClass({JwtDecoder.class, NimbusJwtDecoder.class, JwtAuthenticationConverter.class})
@Conditional(SessionJwtAutoConfiguration.AnyModeConfigured.class)
@EnableConfigurationProperties(SessionJwtProperties.class)
public class SessionJwtAutoConfiguration {

  /** The claim that carries a token's authorities. */
  public static final String ROLES_CLAIM = "roles";

  /**
   * Verifies a session token without calling identity.
   *
   * @param properties the verification mode
   * @return a decoder that rejects any other algorithm, key or issuer
   */
  @Bean
  @ConditionalOnMissingBean(JwtDecoder.class)
  public JwtDecoder sessionJwtDecoder(SessionJwtProperties properties) {
    JwtDecoder session = properties.jwkSetUri() != null ? IssuerKeysDecoder.of(properties) : null;
    JwtDecoder legacy =
        properties.secret() != null ? SharedSecretDecoder.of(properties.secret()) : null;
    if (properties.callers().isEmpty() && properties.hs256Cutoff() == null) {
      return session != null ? session : legacy;
    }
    return new RoutingSessionDecoder(
        session,
        legacy,
        properties.callers(),
        properties.hs256Cutoff(),
        java.time.Clock.systemUTC());
  }

  /**
   * Turns the {@code roles} claim into authorities, verbatim.
   *
   * @return the converter used by the JWT resource server
   */
  @Bean
  @ConditionalOnMissingBean(JwtAuthenticationConverter.class)
  public JwtAuthenticationConverter sessionJwtAuthenticationConverter() {
    JwtGrantedAuthoritiesConverter authorities = new JwtGrantedAuthoritiesConverter();
    authorities.setAuthoritiesClaimName(ROLES_CLAIM);
    authorities.setAuthorityPrefix("");
    JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
    converter.setJwtGrantedAuthoritiesConverter(authorities);
    return converter;
  }

  /** Matches when either verification mode is configured. */
  static class AnyModeConfigured extends AnyNestedCondition {
    AnyModeConfigured() {
      super(ConfigurationPhase.PARSE_CONFIGURATION);
    }

    @ConditionalOnProperty(prefix = "krizaka.security.jwt", name = "secret")
    static class Secret {}

    @ConditionalOnProperty(prefix = "krizaka.security.jwt", name = "jwk-set-uri")
    static class JwkSetUri {}
  }
}
