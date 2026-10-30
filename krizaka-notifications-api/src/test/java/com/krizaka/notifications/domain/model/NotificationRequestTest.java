package com.krizaka.notifications.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class NotificationRequestTest {

  @Test
  void normalisesLocaleRecipientAndVariables() {
    Map<String, String> variables = new HashMap<>(Map.of("name", "Ada"));
    NotificationRequest request =
        new NotificationRequest(Channel.EMAIL, "  ada@example.com ", "welcome", "fr-CA", variables);
    variables.put("name", "changed");

    assertThat(request.recipient()).isEqualTo("ada@example.com");
    assertThat(request.locale()).isEqualTo("fr");
    assertThat(request.variables()).containsEntry("name", "Ada");
  }

  @Test
  void defaultsLocaleAndVariables() {
    NotificationRequest request =
        new NotificationRequest(Channel.SMS, "+15551234567", "otp-code", null, null);

    assertThat(request.locale()).isEqualTo("en");
    assertThat(request.variables()).isEmpty();
  }

  @Test
  void refusesMissingChannelOrRecipient() {
    assertThatThrownBy(() -> new NotificationRequest(null, "a@b.c", "welcome", "en", Map.of()))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(() -> new NotificationRequest(Channel.EMAIL, " ", "welcome", "en", Map.of()))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void refusesTemplateKeysThatCouldEscapeTheTemplateDirectory() {
    assertThatThrownBy(
            () -> new NotificationRequest(Channel.EMAIL, "a@b.c", "../secret", "en", Map.of()))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () -> new NotificationRequest(Channel.EMAIL, "a@b.c", "Welcome", "en", Map.of()))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
