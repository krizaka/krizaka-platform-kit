package com.orazaka.notificationservice.infrastructure.adapter.amqp;

import com.orazaka.notification.domain.model.NotificationRequest;
import com.orazaka.notificationservice.application.service.MessageDedupService;
import com.orazaka.notificationservice.application.service.NotificationService;
import com.orazaka.notificationservice.infrastructure.config.AmqpConstants;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

/**
 * {@code evt.notification.requested} → whatever the producer asked for. This is how any application
 * sends a notification without knowing a provider: publish a {@link NotificationRequest}.
 */
@Component
public class NotificationRequestListener {

  /** Stable dedup identity of this consumer (AGENTS.md §6 messageId idempotency). */
  static final String DEDUP_CONSUMER = "notifications.request";

  private final NotificationService notificationService;
  private final MessageDedupService dedup;

  public NotificationRequestListener(
      NotificationService notificationService, MessageDedupService dedup) {
    this.notificationService = notificationService;
    this.dedup = dedup;
  }

  @RabbitListener(queues = AmqpConstants.REQUESTS_QUEUE)
  public void onNotificationRequested(
      NotificationRequest request,
      @Header(name = AmqpHeaders.MESSAGE_ID, required = false) String messageId) {
    if (!dedup.claim(DEDUP_CONSUMER, messageId)) {
      return;
    }
    try {
      notificationService.send(request);
    } catch (RuntimeException failed) {
      dedup.release(DEDUP_CONSUMER, messageId);
      throw failed;
    }
  }
}
