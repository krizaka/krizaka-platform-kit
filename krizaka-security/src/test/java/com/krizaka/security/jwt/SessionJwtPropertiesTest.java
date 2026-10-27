package com.krizaka.security.jwt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import org.junit.jupiter.api.Test;

class SessionJwtPropertiesTest {

  private static final String SECRET = "a-secret-of-exactly-thirty-two-c";

  @Test
  void acceptsASecretOfThirtyTwoCharacters() {
    assertThat(SECRET).hasSize(SessionJwtProperties.MINIMUM_SECRET_LENGTH);
    assertThat(new SessionJwtProperties(SECRET, null, null, null).secret()).isEqualTo(SECRET);
  }

  @Test
  void refusesAShorterSecret() {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new SessionJwtProperties(SECRET.substring(1), null, null, null))
        .withMessageContaining("at least 32 characters");
  }

  @Test
  void refusesAMissingSecret() {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new SessionJwtProperties(null, null, null, null));
  }

  @Test
  void neverPrintsTheSecret() {
    assertThat(new SessionJwtProperties(SECRET, null, null, null).toString())
        .doesNotContain(SECRET);
  }
}
