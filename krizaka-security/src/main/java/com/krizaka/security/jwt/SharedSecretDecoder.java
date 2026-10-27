package com.krizaka.security.jwt;

import java.nio.charset.StandardCharsets;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

/**
 * The one place the legacy shared HS256 secret is turned into a decoder (ADR-001). An architecture
 * rule forbids the shared-secret API anywhere else, so it can only shrink.
 */
final class SharedSecretDecoder {

  private SharedSecretDecoder() {}

  static JwtDecoder of(String secret) {
    SecretKeySpec key = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
    return NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
  }
}
