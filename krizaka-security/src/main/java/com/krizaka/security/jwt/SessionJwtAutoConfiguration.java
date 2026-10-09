package com.krizaka.security.jwt;

import java.nio.charset.StandardCharsets;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;

/**
 * Local verification of Krizaka session tokens.
 *
 * <p>Active when {@code krizaka.security.jwt.secret} is set. It contributes two beans, each backing
 * off when the application declares its own:
 *
 * <ul>
 *   <li>{@code sessionJwtDecoder} — a Nimbus decoder that accepts only HS256 tokens signed with the
 *       shared secret;
 *   <li>{@code sessionJwtAuthenticationConverter} — maps the {@code roles} claim straight onto
 *       authorities, with <b>no prefix</b>. Identity already emits {@code ROLE_USER}, {@code
 *       ROLE_ADMIN} and {@code SERVICE}; a converter that added {@code SCOPE_} would make a correct
 *       token fail every matcher, and the tempting "fix" is to weaken the matcher.
 * </ul>
 */
@AutoConfiguration
@ConditionalOnClass({JwtDecoder.class, NimbusJwtDecoder.class, JwtAuthenticationConverter.class})
@ConditionalOnProperty(prefix = "krizaka.security.jwt", name = "secret")
@EnableConfigurationProperties(SessionJwtProperties.class)
public class SessionJwtAutoConfiguration {

  /** The claim that carries a token's authorities. */
  public static final String ROLES_CLAIM = "roles";

  /**
   * Verifies a session token against the shared HS256 secret, without calling identity.
   *
   * @param properties the shared secret
   * @return a decoder that rejects any other algorithm or key
   */
  @Bean
  @ConditionalOnMissingBean(JwtDecoder.class)
  public JwtDecoder sessionJwtDecoder(SessionJwtProperties properties) {
    SecretKeySpec key =
        new SecretKeySpec(properties.secret().getBytes(StandardCharsets.UTF_8), "HmacSHA256");
    return NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
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
}
