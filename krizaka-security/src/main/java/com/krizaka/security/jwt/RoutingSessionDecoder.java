package com.krizaka.security.jwt;

import com.krizaka.security.token.PemKeys;
import com.krizaka.security.token.ServiceTokenVerifier;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.JWTParser;
import java.security.interfaces.ECPublicKey;
import java.text.ParseException;
import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;

/**
 * Sends a token to the one verifier its header allows (ADR-001). The algorithm is never taken from
 * the token alone: an ES256 token whose {@code sub} names a registered caller is a {@code SERVICE}
 * token, checked against that caller's own public key and refused unless it carries exactly the
 * {@code SERVICE} role; any other ES256 token is a session (the issuer's keys); HS256 verifies only
 * while the shared secret is configured and before the dated cutover; every other algorithm, {@code
 * none} included, is refused. Holds public keys only.
 */
final class RoutingSessionDecoder implements JwtDecoder {

  private final JwtDecoder session;
  private final JwtDecoder legacy;
  private final JwtDecoder service;
  private final Instant hs256Cutoff;
  private final Clock clock;
  private final Map<String, ECPublicKey> callers;

  RoutingSessionDecoder(
      JwtDecoder session,
      JwtDecoder legacy,
      Map<String, String> callerPems,
      Instant hs256Cutoff,
      Clock clock) {
    this.session = session;
    this.legacy = legacy;
    this.hs256Cutoff = hs256Cutoff;
    this.clock = clock;
    Map<String, ECPublicKey> keys = new HashMap<>();
    callerPems.forEach(
        (name, pem) -> {
          try {
            keys.put(name, PemKeys.publicKey(pem));
          } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                "krizaka.security.jwt.callers." + name + " is not a valid P-256 public key PEM");
          }
        });
    this.callers = Map.copyOf(keys);
    this.service = new ServiceTokenVerifier(keys, clock);
  }

  @Override
  public Jwt decode(String token) {
    com.nimbusds.jose.Header header;
    JWTClaimsSet claims;
    try {
      var parsed = JWTParser.parse(token);
      header = parsed.getHeader();
      claims = parsed instanceof com.nimbusds.jwt.PlainJWT ? null : parsed.getJWTClaimsSet();
    } catch (ParseException e) {
      throw new BadJwtException("Malformed token");
    }
    if (JWSAlgorithm.ES256.equals(header.getAlgorithm())) {
      return decodeEs256(token, claims == null ? null : claims.getSubject());
    }
    if (JWSAlgorithm.HS256.equals(header.getAlgorithm()) && hs256Open()) {
      return legacy.decode(token);
    }
    throw new BadJwtException("Unsupported token algorithm");
  }

  private boolean hs256Open() {
    return legacy != null && (hs256Cutoff == null || clock.instant().isBefore(hs256Cutoff));
  }

  private Jwt decodeEs256(String token, String subject) {
    if (subject != null && callers.containsKey(subject)) {
      return requireServiceRole(service.decode(token));
    }
    if (session == null) {
      throw new BadJwtException("Unknown service caller");
    }
    return session.decode(token);
  }

  private static Jwt requireServiceRole(Jwt jwt) {
    List<String> roles = jwt.getClaimAsStringList("roles");
    if (roles == null || roles.size() != 1 || !"SERVICE".equals(roles.getFirst())) {
      throw new BadJwtException("A caller key signs SERVICE tokens only");
    }
    return jwt;
  }
}
