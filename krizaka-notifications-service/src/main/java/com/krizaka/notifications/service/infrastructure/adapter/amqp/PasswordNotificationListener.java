package com.krizaka.notifications.service.infrastructure.adapter.amqp;

import com.krizaka.messaging.dedup.MessageDedup;
import com.krizaka.notifications.domain.model.Channel;
import com.krizaka.notifications.domain.model.NotificationRequest;
import com.krizaka.notifications.service.application.service.NotificationService;
import com.krizaka.notifications.service.domain.model.PasswordResetRequestedEvent;
import com.krizaka.notifications.service.infrastructure.config.AmqpConstants;
import com.krizaka.notifications.service.infrastructure.config.NotificationProperties;
import java.util.Map;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

/** {@code evt.password.reset} → the password reset e-mail carrying the single-use reset link. */
@Component
public class PasswordNotificationListener {

  /** Stable dedup identity of this consumer (AGENTS.md §6 messageId idempotency). */
  static final String DEDUP_CONSUMER = "notifications.password";

  static final String TEMPLATE = "password-reset";

  private final NotificationService notificationService;
  private final MessageDedup dedup;
  private final NotificationProperties.Links links;

  public PasswordNotificationListener(
      NotificationService notificationService,
      MessageDedup dedup,
      NotificationProperties properties) {
    this.notificationService = notificationService;
    this.dedup = dedup;
    this.links = properties.links();
  }

  @RabbitListener(queues = AmqpConstants.PASSWORD_NOTIFICATIONS_QUEUE)
  public void onPasswordResetRequested(
      PasswordResetRequestedEvent event,
      @Header(name = AmqpHeaders.MESSAGE_ID, required = false) String messageId) {
    if (!dedup.claim(DEDUP_CONSUMER, messageId)) {
      return;
    }
    try {
      notificationService.send(
          new NotificationRequest(
              Channel.EMAIL,
              event.email(),
              TEMPLATE,
              null,
              Map.of("resetUrl", links.resetPasswordUrl(event.plaintextToken()))));
    } catch (RuntimeException failed) {
      dedup.release(DEDUP_CONSUMER, messageId);
      throw failed;
    }
  }
}
