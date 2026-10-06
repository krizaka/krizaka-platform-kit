package com.orazaka.notificationservice.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.orazaka.notification.domain.model.Channel;
import com.orazaka.notification.domain.model.NotificationRequest;
import com.orazaka.notificationservice.domain.exception.TemplateNotFoundException;
import com.orazaka.notificationservice.domain.model.Notification;
import com.orazaka.notificationservice.infrastructure.config.NotificationProperties;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.DefaultResourceLoader;

class NotificationTemplateServiceTest {

  private static NotificationTemplateService service(String location) {
    return new NotificationTemplateService(
        new DefaultResourceLoader(),
        new NotificationProperties(
            "no-reply@test",
            location,
            new NotificationProperties.Links("http://x/v?t={token}", "http://x/r?t={token}"),
            null,
            null));
  }

  @Test
  void rendersTheShippedTemplateInTheRequestedLocale() {
    Notification notification =
        service(null)
            .render(
                new NotificationRequest(
                    Channel.EMAIL,
                    "ada@example.com",
                    "verify-email",
                    "fr",
                    Map.of("username", "Ada", "verificationUrl", "https://app/verify?token=t")));

    assertThat(notification.subject()).isEqualTo("Confirmez votre adresse e-mail");
    assertThat(notification.body())
        .startsWith("Bonjour Ada,")
        .contains("https://app/verify?token=t");
    assertThat(notification.template()).isEqualTo("verify-email");
  }

  @Test
  void fallsBackToEnglish() {
    Notification notification =
        service(null)
            .render(
                new NotificationRequest(
                    Channel.EMAIL, "a@b.c", "password-reset", "de", Map.of("resetUrl", "u")));

    assertThat(notification.subject()).isEqualTo("Reset your password");
  }

  @Test
  void failsOnAMissingVariableInsteadOfSendingAPlaceholder() {
    NotificationRequest request =
        new NotificationRequest(Channel.EMAIL, "a@b.c", "password-reset", "en", Map.of());

    assertThatThrownBy(() -> service(null).render(request))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("resetUrl");
  }

  @Test
  void readsTemplatesFromAnOverrideLocation(@TempDir Path dir) throws Exception {
    Files.createDirectories(dir.resolve("welcome"));
    Files.writeString(dir.resolve("welcome/en.txt"), "Hi {{name}}, $1 is not a group.");

    Notification notification =
        service(dir.toUri().toString())
            .render(
                new NotificationRequest(
                    Channel.SMS, "+15551234567", "welcome", "en", Map.of("name", "Bo")));

    assertThat(notification.subject()).isEmpty();
    assertThat(notification.body()).isEqualTo("Hi Bo, $1 is not a group.");
  }

  @Test
  void reportsAnUnknownTemplate() {
    NotificationRequest request =
        new NotificationRequest(Channel.EMAIL, "a@b.c", "no-such-template", "en", Map.of());

    assertThatThrownBy(() -> service(null).render(request))
        .isInstanceOf(TemplateNotFoundException.class);
  }
}
