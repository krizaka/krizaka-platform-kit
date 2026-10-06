package com.orazaka.notificationservice.infrastructure.adapter.amqp;

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
import java.util.Map;
import org.junit.jupiter.api.Test;

class NotificationRequestListenerTest {

  private final NotificationService notifications = mock(NotificationService.class);
  private final MessageDedupService dedup = mock(MessageDedupService.class);
  private final NotificationRequestListener listener =
      new NotificationRequestListener(notifications, dedup);

  private static final NotificationRequest REQUEST =
      new NotificationRequest(Channel.SMS, "+15551234567", "otp-code", "en", Map.of("code", "1"));

  @Test
  void forwardsAClaimedRequest() {
    when(dedup.claim(NotificationRequestListener.DEDUP_CONSUMER, "m1")).thenReturn(true);

    listener.onNotificationRequested(REQUEST, "m1");

    verify(notifications).send(REQUEST);
  }

  @Test
  void skipsADuplicate() {
    when(dedup.claim(NotificationRequestListener.DEDUP_CONSUMER, "dup")).thenReturn(false);

    listener.onNotificationRequested(REQUEST, "dup");

    verify(notifications, never()).send(any());
  }

  @Test
  void releasesTheClaimWhenDeliveryFails() {
    when(dedup.claim(NotificationRequestListener.DEDUP_CONSUMER, "m2")).thenReturn(true);
    doThrow(new DeliveryException("no provider")).when(notifications).send(any());

    assertThatThrownBy(() -> listener.onNotificationRequested(REQUEST, "m2"))
        .isInstanceOf(DeliveryException.class);
    verify(dedup).release(NotificationRequestListener.DEDUP_CONSUMER, "m2");
  }
}
