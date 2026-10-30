package com.krizaka.security.token;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.crypto.ECDSAVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.security.interfaces.ECPublicKey;
import java.text.ParseException;
import java.time.Clock;
import java.util.Date;
import java.util.Map;
import java.util.Objects;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;

/**
 * Verifies the {@code SERVICE} tokens of ADR-001: each caller signs with its <i>own</i> ES256 key,
 * and this verifier holds only the callers' <b>public</b> keys. The caller is the {@code sub}; the
 * key checked is the one registered for that name, so a caller cannot speak for another and an
 * unknown name is refused. A shared-secret (HS256) token is never accepted here.
 *
 * <p>Thread-safe; holds no private key.
 */
public final class ServiceTokenVerifier implements JwtDecoder {

  private final Map<String, ECPublicKey> callers;
  private final Clock clock;

  /**
   * @param callers the public key of each caller, by the name it puts in {@code sub}
   */
  public ServiceTokenVerifier(Map<String, ECPublicKey> callers) {
    this(callers, Clock.systemUTC());
  }

  /**
   * @param callers the public key of each caller, by the name it puts in {@code sub}
   * @param clock the source of "now" for {@code exp}
   */
  public ServiceTokenVerifier(Map<String, ECPublicKey> callers, Clock clock) {
    this.callers = Map.copyOf(Objects.requireNonNull(callers, "callers"));
    this.clock = Objects.requireNonNull(clock, "clock");
  }

  @Override
  public Jwt decode(String token) {
    try {
      SignedJWT jws = SignedJWT.parse(token);
      if (!JWSAlgorithm.ES256.equals(jws.getHeader().getAlgorithm())) {
        throw new BadJwtException("A service token must be ES256");
      }
      JWTClaimsSet claims = jws.getJWTClaimsSet();
      requireSignedByKnownCaller(jws, claims);
      Date exp = claims.getExpirationTime();
      if (exp == null || !exp.toInstant().isAfter(clock.instant())) {
        throw new BadJwtException("Service token expired");
      }
      return toJwt(token, jws, claims, exp);
    } catch (ParseException | JOSEException e) {
      throw new BadJwtException("Malformed service token", e);
    }
  }

  private void requireSignedByKnownCaller(SignedJWT jws, JWTClaimsSet claims) throws JOSEException {
    ECPublicKey key = callers.get(claims.getSubject());
    if (key == null) {
      throw new BadJwtException("Unknown service caller");
    }
    if (!jws.verify(new ECDSAVerifier(key))) {
      throw new BadJwtException("Invalid service token signature");
    }
  }

  private static Jwt toJwt(String token, SignedJWT jws, JWTClaimsSet claims, Date exp) {
    Date issued = claims.getIssueTime();
    return Jwt.withTokenValue(token)
        .headers(h -> h.putAll(jws.getHeader().toJSONObject()))
        .claims(c -> c.putAll(claims.getClaims()))
        .subject(claims.getSubject())
        .issuedAt(issued == null ? null : issued.toInstant())
        .expiresAt(exp.toInstant())
        .build();
  }
}
