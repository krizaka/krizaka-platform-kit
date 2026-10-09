package com.krizaka.security.jwt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import org.junit.jupiter.api.Test;

class SessionJwtPropertiesTest {

  private static final String SECRET = "a-secret-of-exactly-thirty-two-c";

  @Test
  void acceptsASecretOfThirtyTwoCharacters() {
    assertThat(SECRET).hasSize(SessionJwtProperties.MINIMUM_SECRET_LENGTH);
    assertThat(new SessionJwtProperties(SECRET).secret()).isEqualTo(SECRET);
  }

  @Test
  void refusesAShorterSecret() {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new SessionJwtProperties(SECRET.substring(1)))
        .withMessageContaining("at least 32 characters");
  }

  @Test
  void refusesAMissingSecret() {
    assertThatIllegalArgumentException().isThrownBy(() -> new SessionJwtProperties(null));
  }

  @Test
  void neverPrintsTheSecret() {
    assertThat(new SessionJwtProperties(SECRET).toString()).doesNotContain(SECRET);
  }
}
