package com.krizaka.security.token;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtException;

/**
 * ADR-001 slice 3: each caller signs with its own ES256 key; the verifier knows the public keys.
 */
class ServiceCallerKeysTest {

  /** Keys are generated here, never committed. */
  private static KeyPair keyPair() throws Exception {
    var generator = KeyPairGenerator.getInstance("EC");
    generator.initialize(new ECGenParameterSpec("secp256r1"));
    return generator.generateKeyPair();
  }

  private static String tokenOf(KeyPair pair, String caller) {
    return ServiceTokenProvider.es256((ECPrivateKey) pair.getPrivate(), caller + "-1", caller)
        .token();
  }

  @Test
  void aCallerSignedWithItsOwnKeyIsAccepted() throws Exception {
    KeyPair billing = keyPair();
    var verifier = new ServiceTokenVerifier(Map.of("billing", (ECPublicKey) billing.getPublic()));

    Jwt jwt = verifier.decode(tokenOf(billing, "billing"));

    assertThat(jwt.getSubject()).isEqualTo("billing");
    assertThat(jwt.getClaimAsStringList("roles")).containsExactly("SERVICE");
    assertThat(jwt.getHeaders()).containsEntry("alg", "ES256").containsKey("kid");
  }

  @Test
  void aCallerCannotSpeakForAnother() throws Exception {
    KeyPair billing = keyPair();
    KeyPair studio = keyPair();
    var verifier =
        new ServiceTokenVerifier(
            Map.of(
                "billing", (ECPublicKey) billing.getPublic(),
                "studio", (ECPublicKey) studio.getPublic()));

    // studio's key, billing's name: the key registered for "billing" does not verify it.
    String forged = tokenOf(studio, "billing");

    assertThatThrownBy(() -> verifier.decode(forged)).isInstanceOf(JwtException.class);
  }

  @Test
  void anUnknownCallerIsRefused() throws Exception {
    KeyPair billing = keyPair();
    KeyPair intruder = keyPair();
    var verifier = new ServiceTokenVerifier(Map.of("billing", (ECPublicKey) billing.getPublic()));

    assertThatThrownBy(() -> verifier.decode(tokenOf(intruder, "intruder")))
        .isInstanceOf(JwtException.class);
  }

  @Test
  void anExpiredTokenIsRefused() throws Exception {
    KeyPair billing = keyPair();
    Clock past = Clock.fixed(Instant.now().minusSeconds(3600), ZoneOffset.UTC);
    String old =
        ServiceTokenProvider.es256((ECPrivateKey) billing.getPrivate(), "b-1", "billing", past)
            .token();
    var verifier = new ServiceTokenVerifier(Map.of("billing", (ECPublicKey) billing.getPublic()));

    assertThatThrownBy(() -> verifier.decode(old)).isInstanceOf(JwtException.class);
  }

  @Test
  void aSharedSecretTokenIsNoLongerAccepted() throws Exception {
    KeyPair billing = keyPair();
    var verifier = new ServiceTokenVerifier(Map.of("billing", (ECPublicKey) billing.getPublic()));
    String hs256 =
        new ServiceTokenProvider("krizaka-test-secret-at-least-32-characters!", "billing").token();

    assertThatThrownBy(() -> verifier.decode(hs256)).isInstanceOf(JwtException.class);
  }
}
