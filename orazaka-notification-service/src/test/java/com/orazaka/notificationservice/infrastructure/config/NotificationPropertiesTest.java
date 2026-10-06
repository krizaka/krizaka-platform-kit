package com.orazaka.notificationservice.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

class NotificationPropertiesTest {

  static NotificationProperties.Links links() {
    return new NotificationProperties.Links(
        "https://app.test/verify?token={token}", "https://app.test/reset?token={token}");
  }

  @Test
  void appliesDefaults() {
    NotificationProperties properties =
        new NotificationProperties("no-reply@test", null, links(), null, null);

    assertThat(properties.templateLocation()).isEqualTo("classpath:templates/");
    assertThat(properties.twilio().configured()).isFalse();
    assertThat(properties.twilio().baseUrl()).isEqualTo("https://api.twilio.com");
    assertThat(properties.webhook().allowedHosts()).isEmpty();
  }

  @Test
  void encodesTheTokenIntoTheLinks() {
    assertThat(links().verifyEmailUrl("a b+c")).isEqualTo("https://app.test/verify?token=a+b%2Bc");
    assertThat(links().resetPasswordUrl("xyz")).isEqualTo("https://app.test/reset?token=xyz");
  }

  @Test
  void refusesLinksWithoutATokenPlaceholderAndAMissingSender() {
    assertThatThrownBy(() -> new NotificationProperties.Links("https://app.test/verify", "x"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new NotificationProperties(" ", null, links(), null, null))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void matchesWebhookHostsCaseInsensitively() {
    var webhook = new NotificationProperties.Webhook(List.of(" Hooks.Example.com ", ""));

    assertThat(webhook.allows("hooks.example.COM")).isTrue();
    assertThat(webhook.allows("evil.example.com")).isFalse();
    assertThat(webhook.allows(null)).isFalse();
  }

  @Test
  void twilioIsConfiguredOnlyWithEveryCredential() {
    assertThat(new NotificationProperties.Twilio("AC1", "tok", "+15550000000", null).configured())
        .isTrue();
    assertThat(new NotificationProperties.Twilio("AC1", "", "+15550000000", null).configured())
        .isFalse();
  }
}
