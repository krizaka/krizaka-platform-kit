package com.krizaka.security.web;

import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.annotation.web.configurers.AuthorizeHttpRequestsConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;

/**
 * The rules every Krizaka service's filter chain starts with, written once.
 *
 * <p>Measured across six services, these were the rules that drifted: the ones fixed by hand were
 * consistent, the ones nobody thought about were not. An identity service that answered a CORS
 * preflight with {@code 401} broke every browser call to it; one that required authentication for
 * {@code /error} turned every failure into a second, misleading one. So the baseline is not a
 * convention to copy, it is this method:
 *
 * <ol>
 *   <li>stateless, CSRF off — token-authenticated APIs carry no ambient credential to forge;
 *   <li>{@code OPTIONS /**} open — a CORS preflight never carries a token;
 *   <li>{@code /actuator/health}, {@code /actuator/info} and {@code /error} open;
 *   <li>{@code /internal/v1/**} requires the {@code SERVICE} authority — a real user's token is
 *       refused there, and an unrouted path is not an unreachable one;
 *   <li>then the service's own rules;
 *   <li>then everything else requires authentication — nothing is open by omission.
 * </ol>
 *
 * <p>The authentication mechanism (JWT resource server, opaque token, filters) stays with the
 * service; the baseline decides <i>who may reach what</i>, not <i>how a token is read</i>.
 *
 * <pre>{@code
 * @Bean
 * SecurityFilterChain securityFilterChain(HttpSecurity http, JwtAuthenticationConverter roles)
 *     throws Exception {
 *   return SecurityBaseline.apply(http, auth -> auth.requestMatchers("/api/v1/public/**").permitAll())
 *       .oauth2ResourceServer(o -> o.jwt(jwt -> jwt.jwtAuthenticationConverter(roles)))
 *       .build();
 * }
 * }</pre>
 */
public final class SecurityBaseline {

  /** The authority a service-to-service token carries, exactly as the {@code roles} claim says. */
  public static final String SERVICE_AUTHORITY = "SERVICE";

  /** The machine-to-machine surface, reachable only with {@link #SERVICE_AUTHORITY}. */
  public static final String INTERNAL_PATHS = "/internal/v1/**";

  /** Paths every service answers without a token. */
  public static final String[] PUBLIC_PATHS = {"/actuator/health", "/actuator/info", "/error"};

  private SecurityBaseline() {}

  /**
   * Applies the baseline around the service's own authorization rules.
   *
   * @param http the filter chain under construction
   * @param serviceRules the service's own matchers, evaluated after the baseline's and before the
   *     final {@code anyRequest().authenticated()}; pass {@code auth -> {}} for none
   * @return the same builder, for the service to add its authentication mechanism and build
   * @throws Exception when Spring Security refuses the configuration
   */
  @SuppressWarnings("java:S4502") // CSRF protects cookie sessions; these APIs are token-only.
  public static HttpSecurity apply(
      HttpSecurity http,
      Customizer<
              AuthorizeHttpRequestsConfigurer<HttpSecurity>
                  .AuthorizationManagerRequestMatcherRegistry>
          serviceRules)
      throws Exception {
    return http.csrf(AbstractHttpConfigurer::disable)
        .sessionManagement(
            session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(
            auth -> {
              auth.requestMatchers(HttpMethod.OPTIONS, "/**")
                  .permitAll()
                  .requestMatchers(PUBLIC_PATHS)
                  .permitAll()
                  .requestMatchers(INTERNAL_PATHS)
                  .hasAuthority(SERVICE_AUTHORITY);
              serviceRules.customize(auth);
              auth.anyRequest().authenticated();
            });
  }
}
