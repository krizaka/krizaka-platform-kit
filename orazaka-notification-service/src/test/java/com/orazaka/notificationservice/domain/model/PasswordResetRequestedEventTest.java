package com.orazaka.notificationservice.domain.model;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class PasswordResetRequestedEventTest {

  @Test
  void requiresEmailAndToken() {
    assertThatThrownBy(() -> new PasswordResetRequestedEvent(null, "tok"))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(() -> new PasswordResetRequestedEvent("a@b.c", null))
        .isInstanceOf(NullPointerException.class);
  }
}
