package com.krizaka.notifications.service.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import com.krizaka.test.events.EventContractTest;
import org.junit.jupiter.api.Test;

/**
 * The notification service keeps its own copies of the users events ({@link UserRegisteredEvent},
 * {@link PasswordResetRequestedEvent}); they read what krizaka-users-api's schemas accept.
 * krizaka-users-api is a test dependency only: the service never compiles against it.
 */
class UserEventsCopyContractTest extends EventContractTest {

  @Test
  void userRegisteredReadsIntoTheCopy() {
    UserRegisteredEvent event =
        assertReadable(
            "evt.user.registered",
            1,
            """
            {"user":{"id":"7f9c2a52-1d1e-4a4a-9a51-6a0f3b1c2d3e","username":"ada",
             "email":"ada@example.com","enabled":false,"authorities":["ROLE_USER"],
             "preferences":{"language":"fr"},"activeInterceptions":[],"rateLimitTier":"free"},
             "plaintextToken":"verify-me"}
            """,
            UserRegisteredEvent.class);

    assertThat(event.user().email()).isEqualTo("ada@example.com");
    assertThat(event.user().language()).isEqualTo("fr");
    assertThat(event.requiresVerification()).isTrue();
  }

  @Test
  void userRegisteredWithoutATokenNeedsNoVerification() {
    UserRegisteredEvent event =
        assertReadable(
            "evt.user.registered",
            1,
            """
            {"user":{"id":"7f9c2a52-1d1e-4a4a-9a51-6a0f3b1c2d3e","username":"ada",
             "email":"ada@example.com","enabled":true},"plaintextToken":null}
            """,
            UserRegisteredEvent.class);

    assertThat(event.requiresVerification()).isFalse();
  }

  @Test
  void passwordResetReadsIntoTheCopy() {
    PasswordResetRequestedEvent event =
        assertReadable(
            "evt.password.reset",
            1,
            "{\"email\":\"ada@example.com\",\"plaintextToken\":\"reset-me\"}",
            PasswordResetRequestedEvent.class);

    assertThat(event.plaintextToken()).isEqualTo("reset-me");
  }
}
