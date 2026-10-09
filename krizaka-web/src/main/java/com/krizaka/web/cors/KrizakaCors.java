package com.krizaka.web.cors;

import com.krizaka.web.correlation.CorrelationIdFilter;
import java.util.List;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * The CORS policy of a service that declared its browser origins ({@code
 * krizaka.web.cors.allowed-origins}).
 *
 * <p>Exact origins only; the methods a REST API uses; {@code Authorization}, {@code Content-Type}
 * and {@value CorrelationIdFilter#HEADER} as request headers, and {@value
 * CorrelationIdFilter#HEADER} exposed so a front end can quote it in a bug report. No credentials:
 * Krizaka APIs read a bearer token, not a cookie.
 */
public final class KrizakaCors {

  /** The methods a REST API answers. */
  public static final List<String> ALLOWED_METHODS =
      List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS");

  /** The request headers a browser may send. */
  public static final List<String> ALLOWED_HEADERS =
      List.of("Authorization", "Content-Type", CorrelationIdFilter.HEADER);

  /** How long a browser may cache a preflight answer, in seconds. */
  public static final long MAX_AGE_SECONDS = 1800;

  private KrizakaCors() {}

  /**
   * The policy for these origins.
   *
   * @param allowedOrigins the declared origins
   * @return a new configuration
   */
  public static CorsConfiguration configuration(List<String> allowedOrigins) {
    CorsConfiguration cors = new CorsConfiguration();
    cors.setAllowedOrigins(List.copyOf(allowedOrigins));
    cors.setAllowedMethods(ALLOWED_METHODS);
    cors.setAllowedHeaders(ALLOWED_HEADERS);
    cors.setExposedHeaders(List.of(CorrelationIdFilter.HEADER));
    cors.setMaxAge(MAX_AGE_SECONDS);
    return cors;
  }

  /**
   * The policy applied to every path.
   *
   * @param allowedOrigins the declared origins
   * @return a source mapping {@code /**} to {@link #configuration(List)}
   */
  public static UrlBasedCorsConfigurationSource source(List<String> allowedOrigins) {
    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/**", configuration(allowedOrigins));
    return source;
  }
}
