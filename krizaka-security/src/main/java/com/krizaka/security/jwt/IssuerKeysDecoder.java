package com.krizaka.security.jwt;

import java.util.List;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

/**
 * Verifies session tokens against the issuer's published keys: ES256 only, one issuer, optionally
 * one audience. Holds no signing material.
 */
final class IssuerKeysDecoder {

  private IssuerKeysDecoder() {}

  static JwtDecoder of(SessionJwtProperties properties) {
    NimbusJwtDecoder decoder =
        NimbusJwtDecoder.withJwkSetUri(properties.jwkSetUri())
            .jwsAlgorithm(SignatureAlgorithm.ES256)
            .build();
    OAuth2TokenValidator<Jwt> audience =
        properties.audience() == null
            ? jwt -> OAuth2TokenValidatorResult.success()
            : jwt ->
                jwt.getAudience() != null && jwt.getAudience().contains(properties.audience())
                    ? OAuth2TokenValidatorResult.success()
                    : OAuth2TokenValidatorResult.failure(
                        new OAuth2Error("invalid_token", "wrong audience", null));
    decoder.setJwtValidator(
        new DelegatingOAuth2TokenValidator<>(
            List.of(JwtValidators.createDefaultWithIssuer(properties.issuer()), audience)));
    return decoder;
  }
}
