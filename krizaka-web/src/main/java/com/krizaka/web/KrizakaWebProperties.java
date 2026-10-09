package com.krizaka.web;

import java.net.URI;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * The HTTP baseline a service declares ({@code krizaka.web}).
 *
 * <p>Every default is applied here, in the record, so a service that sets nothing gets the same
 * answer as one that reads this class — and a value that would silently weaken the contract (a
 * relative problem base, a wildcard origin) fails at startup with the reason.
 *
 * @param problems how Problem Details {@code type} URIs are built
 * @param cors the browser origins this service answers; none by default
 */
@ConfigurationProperties(prefix = "krizaka.web")
public record KrizakaWebProperties(Problems problems, Cors cors) {

  /** Normalises the defaults. */
  public KrizakaWebProperties {
    problems = problems == null ? new Problems(null) : problems;
    cors = cors == null ? new Cors(null) : cors;
  }

  /**
   * Problem Details settings ({@code krizaka.web.problems}).
   *
   * @param baseType the prefix every problem {@code type} is resolved against — {@code
   *     <baseType><code>}; absolute and ending with {@code /}. Defaults to {@value
   *     #DEFAULT_BASE_TYPE}
   */
  public record Problems(URI baseType) {

    /** Where the stable error codes are documented. */
    public static final String DEFAULT_BASE_TYPE = "https://krizaka.com/problems/";

    /**
     * Applies the default and rejects a base that would resolve codes to the wrong URI.
     *
     * @throws IllegalArgumentException when the base is relative or does not end with {@code /}
     *     ({@code https://x/problems} resolves {@code not-found} to {@code https://x/not-found})
     */
    public Problems {
      baseType = baseType == null ? URI.create(DEFAULT_BASE_TYPE) : baseType;
      if (!baseType.isAbsolute() || !baseType.getPath().endsWith("/")) {
        throw new IllegalArgumentException(
            "krizaka.web.problems.base-type must be an absolute URI ending with '/': " + baseType);
      }
    }
  }

  /**
   * Browser origins ({@code krizaka.web.cors}).
   *
   * @param allowedOrigins the exact origins (scheme, host, port) a browser may call this service
   *     from; empty means none — a service without a front end opens nothing
   */
  public record Cors(List<String> allowedOrigins) {

    /**
     * Copies the list and rejects an origin that is not one.
     *
     * @throws IllegalArgumentException for a blank entry or {@code *}: a wildcard is the absence of
     *     a declaration, not one
     */
    public Cors {
      allowedOrigins = allowedOrigins == null ? List.of() : List.copyOf(allowedOrigins);
      for (String origin : allowedOrigins) {
        if (origin.isBlank() || origin.contains("*")) {
          throw new IllegalArgumentException(
              "krizaka.web.cors.allowed-origins lists exact origins, not '" + origin + "'");
        }
      }
    }
  }
}
