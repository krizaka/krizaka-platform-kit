package com.orazaka.notificationservice.infrastructure.adapter.amqp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.orazaka.notification.domain.model.Channel;
import com.orazaka.notification.domain.model.NotificationRequest;
import com.orazaka.notificationservice.application.service.MessageDedupService;
import com.orazaka.notificationservice.application.service.NotificationService;
import com.orazaka.notificationservice.domain.exception.DeliveryException;
import com.orazaka.notificationservice.domain.model.UserRegisteredEvent;
import com.orazaka.notificationservice.infrastructure.config.NotificationProperties;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class UserNotificationListenerTest {

  private final NotificationService notifications = mock(NotificationService.class);
  private final MessageDedupService dedup = mock(MessageDedupService.class);
  private final UserNotificationListener listener =
      new UserNotificationListener(
          notifications,
          dedup,
          new NotificationProperties(
              "no-reply@test",
              null,
              new NotificationProperties.Links(
                  "https://app.test/verify?token={token}", "https://app.test/reset?token={token}"),
              null,
              null));

  private static UserRegisteredEvent registered(String token) {
    return new UserRegisteredEvent(
        new UserRegisteredEvent.UserSummary("ada@example.com", "ada", Map.of("language", "fr")),
        token);
  }

  @Test
  void sendsTheVerificationMailInTheUsersLanguage() {
    when(dedup.claim(UserNotificationListener.DEDUP_CONSUMER, "m1")).thenReturn(true);

    listener.onUserRegistered(registered("tok"), "m1");

    ArgumentCaptor<NotificationRequest> sent = ArgumentCaptor.forClass(NotificationRequest.class);
    verify(notifications).send(sent.capture());
    assertThat(sent.getValue().channel()).isEqualTo(Channel.EMAIL);
    assertThat(sent.getValue().template()).isEqualTo("verify-email");
    assertThat(sent.getValue().locale()).isEqualTo("fr");
    assertThat(sent.getValue().variables())
        .containsEntry("verificationUrl", "https://app.test/verify?token=tok")
        .containsEntry("username", "ada");
  }

  @Test
  void skipsDuplicatesAndRegistrationsWithoutToken() {
    when(dedup.claim(UserNotificationListener.DEDUP_CONSUMER, "dup")).thenReturn(false);

    listener.onUserRegistered(registered("tok"), "dup");
    listener.onUserRegistered(registered(null), "m2");

    verify(notifications, never()).send(any());
  }

  @Test
  void releasesTheClaimWhenDeliveryFails() {
    when(dedup.claim(UserNotificationListener.DEDUP_CONSUMER, "m3")).thenReturn(true);
    doThrow(new DeliveryException("down")).when(notifications).send(any());

    assertThatThrownBy(() -> listener.onUserRegistered(registered("tok"), "m3"))
        .isInstanceOf(DeliveryException.class);
    verify(dedup).release(UserNotificationListener.DEDUP_CONSUMER, "m3");
  }
}
