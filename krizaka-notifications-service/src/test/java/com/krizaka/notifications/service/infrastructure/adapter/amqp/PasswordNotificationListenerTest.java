package com.krizaka.notifications.service.infrastructure.adapter.amqp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.krizaka.messaging.dedup.MessageDedup;
import com.krizaka.notifications.domain.model.NotificationRequest;
import com.krizaka.notifications.service.application.service.NotificationService;
import com.krizaka.notifications.service.domain.exception.DeliveryException;
import com.krizaka.notifications.service.domain.model.PasswordResetRequestedEvent;
import com.krizaka.notifications.service.infrastructure.config.NotificationProperties;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class PasswordNotificationListenerTest {

  private final NotificationService notifications = mock(NotificationService.class);
  private final MessageDedup dedup = mock(MessageDedup.class);
  private final PasswordNotificationListener listener =
      new PasswordNotificationListener(
          notifications,
          dedup,
          new NotificationProperties(
              "no-reply@test",
              null,
              new NotificationProperties.Links(
                  "https://app.test/verify?token={token}", "https://app.test/reset?token={token}"),
              null,
              null));

  private static final PasswordResetRequestedEvent RESET =
      new PasswordResetRequestedEvent("ada@example.com", "tok");

  @Test
  void sendsTheResetLink() {
    when(dedup.claim(PasswordNotificationListener.DEDUP_CONSUMER, "m1")).thenReturn(true);

    listener.onPasswordResetRequested(RESET, "m1");

    ArgumentCaptor<NotificationRequest> sent = ArgumentCaptor.forClass(NotificationRequest.class);
    verify(notifications).send(sent.capture());
    assertThat(sent.getValue().template()).isEqualTo("password-reset");
    assertThat(sent.getValue().recipient()).isEqualTo("ada@example.com");
    assertThat(sent.getValue().variables())
        .containsEntry("resetUrl", "https://app.test/reset?token=tok");
  }

  @Test
  void skipsADuplicate() {
    when(dedup.claim(PasswordNotificationListener.DEDUP_CONSUMER, "dup")).thenReturn(false);

    listener.onPasswordResetRequested(RESET, "dup");

    verify(notifications, never()).send(any());
  }

  @Test
  void releasesTheClaimWhenDeliveryFails() {
    when(dedup.claim(PasswordNotificationListener.DEDUP_CONSUMER, "m2")).thenReturn(true);
    doThrow(new DeliveryException("down")).when(notifications).send(any());

    assertThatThrownBy(() -> listener.onPasswordResetRequested(RESET, "m2"))
        .isInstanceOf(DeliveryException.class);
    verify(dedup).release(PasswordNotificationListener.DEDUP_CONSUMER, "m2");
  }
}
