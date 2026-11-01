package com.krizaka.security.jwt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.krizaka.security.token.ServiceTokenProvider;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.ECDSASigner;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.PlainJWT;
import com.nimbusds.jwt.SignedJWT;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.ECPrivateKey;
import java.security.spec.ECGenParameterSpec;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;

/**
 * ADR-001 slice 4, the callee side: a service that accepts the SERVICE tokens of its callers by
 * their own public keys. Keys are generated here and the PEM markers built from constants, never
 * committed.
 */
class RoutingSessionDecoderTest {

  private static final String SECRET = "krizaka-test-secret-at-least-32-characters!";
  private static final Instant NOW = Instant.parse("2026-12-01T12:00:00Z");
  private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
  private static final String DASHES = "-----";
  private static final String PUBLIC = "PUBLIC KEY";

  private final JwtDecoder legacy = SharedSecretDecoder.of(SECRET);
  private final JwtDecoder session =
      token -> {
        throw new BadJwtException("session decoder reached");
      };

  private static KeyPair keyPair() throws Exception {
    var generator = KeyPairGenerator.getInstance("EC");
    generator.initialize(new ECGenParameterSpec("secp256r1"));
    return generator.generateKeyPair();
  }

  private static String pem(KeyPair pair) {
    String body =
        Base64.getMimeEncoder(64, "\n".getBytes(StandardCharsets.UTF_8))
            .encodeToString(pair.getPublic().getEncoded());
    return DASHES + "BEGIN " + PUBLIC + DASHES + "\n" + body + "\n" + DASHES + "END " + PUBLIC
        + DASHES;
  }

  private static String serviceToken(KeyPair pair, String caller, Clock clock) {
    return ServiceTokenProvider.es256(
            (ECPrivateKey) pair.getPrivate(), caller + "-1", caller, clock)
        .token();
  }

  private RoutingSessionDecoder decoder(Map<String, String> callers, Instant cutoff) {
    return new RoutingSessionDecoder(session, legacy, callers, cutoff, CLOCK);
  }

  private static String hs256(String subject, List<String> roles) throws Exception {
    SignedJWT jwt =
        new SignedJWT(
            new JWSHeader(JWSAlgorithm.HS256),
            new JWTClaimsSet.Builder()
                .subject(subject)
                .claim("roles", roles)
                .expirationTime(Date.from(NOW.plusSeconds(300)))
                .build());
    jwt.sign(new MACSigner(SECRET.getBytes(StandardCharsets.UTF_8)));
    return jwt.serialize();
  }

  @Test
  void aRegisteredCallerSignedWithItsOwnKeyIsAcceptedAsService() throws Exception {
    KeyPair studio = keyPair();
    var decoder = decoder(Map.of("studio", pem(studio)), null);

    Jwt jwt = decoder.decode(serviceToken(studio, "studio", CLOCK));

    assertThat(jwt.getSubject()).isEqualTo("studio");
    assertThat(jwt.getClaimAsStringList("roles")).containsExactly("SERVICE");
  }

  @Test
  void aTokenSignedWithAnotherKeyIsRefused() throws Exception {
    var decoder = decoder(Map.of("studio", pem(keyPair())), null);

    assertThatThrownBy(() -> decoder.decode(serviceToken(keyPair(), "studio", CLOCK)))
        .isInstanceOf(BadJwtException.class);
  }

  @Test
  void anUnknownCallerIsRefusedAndNeverReachesTheSessionKeys() throws Exception {
    var decoder =
        new RoutingSessionDecoder(null, legacy, Map.of("studio", pem(keyPair())), null, CLOCK);

    assertThatThrownBy(() -> decoder.decode(serviceToken(keyPair(), "intruder", CLOCK)))
        .isInstanceOf(BadJwtException.class)
        .hasMessageContaining("Unknown service caller");
  }

  @Test
  void anExpiredTokenIsRefused() throws Exception {
    KeyPair studio = keyPair();
    var decoder = decoder(Map.of("studio", pem(studio)), null);
    Clock past = Clock.fixed(NOW.minusSeconds(3600), ZoneOffset.UTC);

    assertThatThrownBy(() -> decoder.decode(serviceToken(studio, "studio", past)))
        .isInstanceOf(BadJwtException.class);
  }

  @Test
  void aTamperedTokenIsRefused() throws Exception {
    KeyPair studio = keyPair();
    var decoder = decoder(Map.of("studio", pem(studio)), null);
    String[] parts = serviceToken(studio, "studio", CLOCK).split("\\.");
    String claims =
        new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8)
            .replace("studio", "studiO");
    String tampered =
        parts[0]
            + "."
            + Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(claims.getBytes(StandardCharsets.UTF_8))
            + "."
            + parts[2];

    assertThatThrownBy(() -> decoder.decode(tampered)).isInstanceOf(BadJwtException.class);
  }

  @Test
  void aCallerKeyThatSignsAnythingButServiceIsRefused() throws Exception {
    KeyPair studio = keyPair();
    var decoder = decoder(Map.of("studio", pem(studio)), null);
    SignedJWT admin =
        new SignedJWT(
            new JWSHeader.Builder(JWSAlgorithm.ES256).keyID("studio-1").build(),
            new JWTClaimsSet.Builder()
                .subject("studio")
                .claim("roles", List.of("ROLE_ADMIN"))
                .expirationTime(Date.from(NOW.plusSeconds(300)))
                .build());
    admin.sign(new ECDSASigner((ECPrivateKey) studio.getPrivate()));

    assertThatThrownBy(() -> decoder.decode(admin.serialize())).isInstanceOf(BadJwtException.class);
  }

  @Test
  void algNoneIsRefused() {
    var decoder = decoder(Map.of(), null);
    String none =
        new PlainJWT(
                new JWTClaimsSet.Builder()
                    .subject("studio")
                    .claim("roles", List.of("SERVICE"))
                    .expirationTime(Date.from(NOW.plusSeconds(300)))
                    .build())
            .serialize();

    assertThatThrownBy(() -> decoder.decode(none)).isInstanceOf(BadJwtException.class);
  }

  @Test
  void hs256IsAcceptedBeforeTheCutoffAndRefusedAfter() throws Exception {
    String token = hs256("studio", List.of("SERVICE"));

    assertThat(decoder(Map.of(), NOW.plusSeconds(60)).decode(token).getSubject())
        .isEqualTo("studio");
    assertThatThrownBy(() -> decoder(Map.of(), NOW.minusSeconds(60)).decode(token))
        .isInstanceOf(BadJwtException.class);
  }

  @Test
  void hs256IsRefusedWhenNoSecretIsConfigured() throws Exception {
    var decoder = new RoutingSessionDecoder(session, null, Map.of(), null, CLOCK);

    assertThatThrownBy(() -> decoder.decode(hs256("studio", List.of("SERVICE"))))
        .isInstanceOf(BadJwtException.class);
  }

  @Test
  void aNonCallerEs256TokenGoesToTheIssuerKeys() throws Exception {
    KeyPair user = keyPair();
    var decoder = decoder(Map.of("studio", pem(keyPair())), null);

    assertThatThrownBy(() -> decoder.decode(serviceToken(user, "user-1", CLOCK)))
        .hasMessageContaining("session decoder reached");
  }

  @Test
  void aBadCallerKeyFailsAtStartup() {
    assertThatThrownBy(() -> decoder(Map.of("studio", "not a key"), null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("krizaka.security.jwt.callers.studio");
  }
}
