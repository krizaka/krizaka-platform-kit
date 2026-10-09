package com.krizaka.security.token;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.krizaka.security.jwt.SessionJwtAutoConfiguration;
import com.krizaka.security.jwt.SessionJwtProperties;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;

class ServiceTokenProviderTest {

  private static final String SECRET = "krizaka-test-secret-at-least-32-characters!";

  private final JwtDecoder decoder =
      new SessionJwtAutoConfiguration().sessionJwtDecoder(new SessionJwtProperties(SECRET));

  @Test
  void mintsATokenTheSessionDecoderAcceptsWithTheServiceRole() {
    Jwt jwt = decoder.decode(new ServiceTokenProvider(SECRET, "billing-client").token());

    assertThat(jwt.getSubject()).isEqualTo("billing-client");
    assertThat(jwt.getClaimAsStringList("roles")).containsExactly("SERVICE");
  }

  @Test
  void expiresFiveMinutesAfterIssue() {
    Instant now = Instant.now();
    Clock clock = Clock.fixed(now, ZoneOffset.UTC);

    Jwt jwt = decoder.decode(new ServiceTokenProvider(SECRET, "studio", clock).token());

    assertThat(jwt.getIssuedAt()).isEqualTo(now.truncatedTo(java.time.temporal.ChronoUnit.SECONDS));
    assertThat(jwt.getExpiresAt()).isEqualTo(jwt.getIssuedAt().plus(ServiceTokenProvider.LIFETIME));
  }

  @Test
  void aTokenSignedWithAnotherSecretIsRejected() {
    String forged =
        new ServiceTokenProvider("another-secret-that-is-also-32-chars-long", "intruder").token();

    assertThatThrownBy(() -> decoder.decode(forged)).isInstanceOf(JwtException.class);
  }

  @Test
  void refusesAShortSecret() {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new ServiceTokenProvider("short", "billing-client"));
  }

  @Test
  void refusesAMissingSecret() {
    assertThatNullPointerException().isThrownBy(() -> new ServiceTokenProvider(null, "x"));
  }

  @Test
  void refusesASubjectThatCouldBreakTheClaims() {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new ServiceTokenProvider(SECRET, "a\",\"roles\":[\"ADMIN"));
    assertThatIllegalArgumentException().isThrownBy(() -> new ServiceTokenProvider(SECRET, " "));
  }
}
