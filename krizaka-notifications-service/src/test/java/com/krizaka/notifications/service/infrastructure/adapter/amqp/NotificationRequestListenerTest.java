package com.krizaka.notifications.service.infrastructure.adapter.amqp;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.krizaka.messaging.dedup.MessageDedup;
import com.krizaka.notifications.domain.model.Channel;
import com.krizaka.notifications.domain.model.NotificationRequest;
import com.krizaka.notifications.service.application.service.NotificationService;
import com.krizaka.notifications.service.domain.exception.DeliveryException;
import java.util.Map;
import org.junit.jupiter.api.Test;

class NotificationRequestListenerTest {

  private final NotificationService notifications = mock(NotificationService.class);
  private final MessageDedup dedup = mock(MessageDedup.class);
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
