package com.krizaka.observability;

import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * The names a service is observed under ({@code krizaka.observability}).
 *
 * <p>All three are <b>required</b>: a metric, a trace or a log line that cannot say which product,
 * which service and which build produced it is noise on a shared dashboard. A service that does not
 * name itself does not start, and the failure lists every missing key.
 *
 * <pre>{@code
 * krizaka:
 *   observability: { product: orazaka, service: conversation, version: "@project.version@" }
 * }</pre>
 *
 * @param product the product the service belongs to (e.g. {@code orazaka})
 * @param service the service's short name within the product (e.g. {@code conversation})
 * @param version the running build's version, usually filtered in by the build
 */
@ConfigurationProperties(prefix = "krizaka.observability")
public record KrizakaObservabilityProperties(String product, String service, String version) {

  /** The prefix of the three properties, for messages and tests. */
  public static final String PREFIX = "krizaka.observability";

  /**
   * Rejects a service that does not name itself.
   *
   * @throws IllegalArgumentException naming every missing or blank property
   */
  public KrizakaObservabilityProperties {
    List<String> missing = new ArrayList<>();
    if (isBlank(product)) {
      missing.add(PREFIX + ".product");
    }
    if (isBlank(service)) {
      missing.add(PREFIX + ".service");
    }
    if (isBlank(version)) {
      missing.add(PREFIX + ".version");
    }
    if (!missing.isEmpty()) {
      throw new IllegalArgumentException(
          "A Krizaka service must name itself to be observed; missing: "
              + String.join(", ", missing)
              + " (e.g. krizaka.observability: { product: acme, service: orders,"
              + " version: \"@project.version@\" })");
    }
  }

  private static boolean isBlank(String value) {
    return value == null || value.isBlank();
  }
}
