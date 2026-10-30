package com.krizaka.notifications.service.infrastructure.adapter.amqp;

import com.krizaka.messaging.dedup.MessageDedup;
import com.krizaka.notifications.domain.model.Channel;
import com.krizaka.notifications.domain.model.NotificationRequest;
import com.krizaka.notifications.service.application.service.NotificationService;
import com.krizaka.notifications.service.domain.model.UserRegisteredEvent;
import com.krizaka.notifications.service.infrastructure.config.AmqpConstants;
import com.krizaka.notifications.service.infrastructure.config.NotificationProperties;
import java.util.Map;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

/**
 * {@code evt.user.registered} → the verification e-mail, in the user's language, linking to the
 * application's verification page. A registration that issued no token needs no e-mail.
 */
@Component
public class UserNotificationListener {

  /** Stable dedup identity of this consumer (AGENTS.md §6 messageId idempotency). */
  static final String DEDUP_CONSUMER = "notifications.user";

  static final String TEMPLATE = "verify-email";

  private final NotificationService notificationService;
  private final MessageDedup dedup;
  private final NotificationProperties.Links links;

  public UserNotificationListener(
      NotificationService notificationService,
      MessageDedup dedup,
      NotificationProperties properties) {
    this.notificationService = notificationService;
    this.dedup = dedup;
    this.links = properties.links();
  }

  @RabbitListener(queues = AmqpConstants.USER_NOTIFICATIONS_QUEUE)
  public void onUserRegistered(
      UserRegisteredEvent event,
      @Header(name = AmqpHeaders.MESSAGE_ID, required = false) String messageId) {
    if (!event.requiresVerification() || !dedup.claim(DEDUP_CONSUMER, messageId)) {
      return;
    }
    try {
      notificationService.send(
          new NotificationRequest(
              Channel.EMAIL,
              event.user().email(),
              TEMPLATE,
              event.user().language(),
              Map.of(
                  "username",
                  event.user().username(),
                  "verificationUrl",
                  links.verifyEmailUrl(event.plaintextToken()))));
    } catch (RuntimeException failed) {
      dedup.release(DEDUP_CONSUMER, messageId);
      throw failed;
    }
  }
}
