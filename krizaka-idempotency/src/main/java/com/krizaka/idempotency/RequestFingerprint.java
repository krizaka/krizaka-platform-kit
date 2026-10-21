package com.krizaka.idempotency;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;
import tools.jackson.databind.MapperFeature;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * The SHA-256 of a request in canonical JSON: properties and map keys sorted, so two requests that
 * differ only in the order of their fields have one fingerprint.
 */
final class RequestFingerprint {

  private final JsonMapper canonical;

  RequestFingerprint(JsonMapper json) {
    this.canonical =
        Objects.requireNonNull(json, "json")
            .rebuild()
            .enable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY)
            .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
            .build();
  }

  String of(String scope, Object body) {
    String text = canonical.writeValueAsString(scope) + "|" + canonical.writeValueAsString(body);
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 is part of every JDK", e);
    }
  }
}
