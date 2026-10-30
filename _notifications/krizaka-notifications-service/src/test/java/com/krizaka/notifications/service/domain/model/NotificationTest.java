package com.krizaka.notifications.service.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.krizaka.notifications.domain.model.Channel;
import org.junit.jupiter.api.Test;

class NotificationTest {

  @Test
  void defaultsAMissingSubjectToEmpty() {
    Notification notification = new Notification(Channel.SMS, "+15551234567", "otp", null, "1234");

    assertThat(notification.subject()).isEmpty();
  }

  @Test
  void refusesAnEmptyBodyOrRecipient() {
    assertThatThrownBy(() -> new Notification(Channel.EMAIL, "a@b.c", "t", "s", " "))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new Notification(Channel.EMAIL, "", "t", "s", "body"))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
