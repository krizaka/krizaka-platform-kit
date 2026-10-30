package com.krizaka.notifications.service.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import org.junit.jupiter.api.Test;

class UserRegisteredEventTest {

  @Test
  void readsLanguageAndFallsBackToEnglish() {
    var french = new UserRegisteredEvent.UserSummary("a@b.c", "ada", Map.of("language", "fr"));
    var unknown = new UserRegisteredEvent.UserSummary("a@b.c", null, null);

    assertThat(french.language()).isEqualTo("fr");
    assertThat(unknown.language()).isEqualTo("en");
    assertThat(unknown.username()).isEqualTo("a@b.c");
  }

  @Test
  void requiresVerificationOnlyWhenATokenWasIssued() {
    var user = new UserRegisteredEvent.UserSummary("a@b.c", "ada", Map.of());

    assertThat(new UserRegisteredEvent(user, "tok").requiresVerification()).isTrue();
    assertThat(new UserRegisteredEvent(user, null).requiresVerification()).isFalse();
    assertThatThrownBy(() -> new UserRegisteredEvent(null, "tok"))
        .isInstanceOf(NullPointerException.class);
  }
}
