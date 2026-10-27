package com.krizaka.security.jwt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.ECDSASigner;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.gen.ECKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;

/**
 * Slice 1 of ADR-001: verification against the issuer's published keys. Keys are generated here.
 */
class SessionJwksVerificationTest {

  private static final String ISSUER = "https://users.example.test";
  private static final String OLD_SECRET = "krizaka-test-secret-at-least-32-characters!";

  private final AtomicReference<JWKSet> published = new AtomicReference<>();
  private HttpServer server;
  private String jwksUri;
  private ECKey issuerKey;

  private final ApplicationContextRunner runner =
      new ApplicationContextRunner()
          .withConfiguration(AutoConfigurations.of(SessionJwtAutoConfiguration.class));

  @BeforeEach
  void startIssuer() throws Exception {
    issuerKey = new ECKeyGenerator(Curve.P_256).keyID("k1").generate();
    published.set(new JWKSet(issuerKey.toPublicJWK()));
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext(
        "/jwks.json",
        exchange -> {
          byte[] body = published.get().toString().getBytes(StandardCharsets.UTF_8);
          exchange.getResponseHeaders().add("Content-Type", "application/json");
          exchange.sendResponseHeaders(200, body.length);
          exchange.getResponseBody().write(body);
          exchange.close();
        });
    server.start();
    jwksUri = "http://127.0.0.1:" + server.getAddress().getPort() + "/jwks.json";
  }

  @AfterEach
  void stopIssuer() {
    server.stop(0);
  }

  private static String sign(ECKey key, String issuer) throws Exception {
    JWTClaimsSet claims =
        new JWTClaimsSet.Builder()
            .subject("user-1")
            .issuer(issuer)
            .claim("roles", List.of("ROLE_USER"))
            .expirationTime(Date.from(Instant.now().plusSeconds(300)))
            .build();
    SignedJWT jwt =
        new SignedJWT(
            new JWSHeader.Builder(JWSAlgorithm.ES256).keyID(key.getKeyID()).build(), claims);
    jwt.sign(new ECDSASigner(key));
    return jwt.serialize();
  }

  private void withDecoder(ThrowingConsumer test) {
    runner
        .withPropertyValues(
            "krizaka.security.jwt.jwk-set-uri=" + jwksUri, "krizaka.security.jwt.issuer=" + ISSUER)
        .run(context -> test.accept(context.getBean(JwtDecoder.class)));
  }

  private interface ThrowingConsumer {
    void accept(JwtDecoder decoder) throws Exception;
  }

  @Test
  void acceptsATokenSignedByTheIssuer() {
    withDecoder(
        decoder ->
            assertThat(decoder.decode(sign(issuerKey, ISSUER)).getSubject()).isEqualTo("user-1"));
  }

  @Test
  void refusesATokenSignedByAnUnknownKey() {
    withDecoder(
        decoder -> {
          ECKey stranger = new ECKeyGenerator(Curve.P_256).keyID("k1").generate();
          assertThatThrownBy(() -> decoder.decode(sign(stranger, ISSUER)))
              .isInstanceOf(JwtException.class);
        });
  }

  @Test
  void refusesATokenFromAnotherIssuer() {
    withDecoder(
        decoder ->
            assertThatThrownBy(() -> decoder.decode(sign(issuerKey, "https://evil.example.test")))
                .isInstanceOf(JwtException.class));
  }

  @Test
  void refusesATokenSignedWithTheOldSharedSecret() {
    withDecoder(
        decoder -> {
          SignedJWT jwt =
              new SignedJWT(
                  new JWSHeader.Builder(JWSAlgorithm.HS256).keyID("k1").build(),
                  new JWTClaimsSet.Builder()
                      .subject("user-1")
                      .issuer(ISSUER)
                      .expirationTime(Date.from(Instant.now().plusSeconds(300)))
                      .build());
          jwt.sign(new MACSigner(OLD_SECRET.getBytes(StandardCharsets.UTF_8)));
          assertThatThrownBy(() -> decoder.decode(jwt.serialize()))
              .isInstanceOf(JwtException.class);
        });
  }

  @Test
  void rotationKeepsTheOldKidAndAcceptsTheNewOne() {
    withDecoder(
        decoder -> {
          String before = sign(issuerKey, ISSUER);
          ECKey next = new ECKeyGenerator(Curve.P_256).keyID("k2").generate();
          published.set(new JWKSet(List.of(issuerKey.toPublicJWK(), next.toPublicJWK())));
          assertThat(decoder.decode(sign(next, ISSUER)).getSubject()).isEqualTo("user-1");
          assertThat(decoder.decode(before).getSubject()).isEqualTo("user-1");
        });
  }

  @Test
  void anExpiredOldKidIsRefusedOnceTheIssuerWithdrawsIt() {
    withDecoder(
        decoder -> {
          ECKey next = new ECKeyGenerator(Curve.P_256).keyID("k2").generate();
          published.set(new JWKSet(next.toPublicJWK()));
          assertThat(decoder.decode(sign(next, ISSUER)).getSubject()).isEqualTo("user-1");
          assertThatThrownBy(() -> decoder.decode(sign(issuerKey, ISSUER)))
              .isInstanceOf(JwtException.class);
        });
  }

  @Test
  void enforcesTheAudienceWhenConfigured() {
    runner
        .withPropertyValues(
            "krizaka.security.jwt.jwk-set-uri=" + jwksUri,
            "krizaka.security.jwt.issuer=" + ISSUER,
            "krizaka.security.jwt.audience=orazaka")
        .run(
            context ->
                assertThatThrownBy(
                        () -> context.getBean(JwtDecoder.class).decode(sign(issuerKey, ISSUER)))
                    .isInstanceOf(JwtException.class));
  }

  @Test
  void bothModesTogetherFailAtStartup() {
    runner
        .withPropertyValues(
            "krizaka.security.jwt.jwk-set-uri=" + jwksUri,
            "krizaka.security.jwt.issuer=" + ISSUER,
            "krizaka.security.jwt.secret=" + OLD_SECRET)
        .run(context -> assertThat(context).hasFailed());
  }

  @Test
  void aJwkSetUriWithoutAnIssuerFailsAtStartup() {
    runner
        .withPropertyValues("krizaka.security.jwt.jwk-set-uri=" + jwksUri)
        .run(context -> assertThat(context).hasFailed());
  }

  @Test
  void theSecretNeverAppearsInTheDescription() {
    assertThat(new SessionJwtProperties(OLD_SECRET, null, null, null).toString())
        .doesNotContain(OLD_SECRET);
  }
}
