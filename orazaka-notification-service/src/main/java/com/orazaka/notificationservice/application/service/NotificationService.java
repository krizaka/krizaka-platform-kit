package com.orazaka.notificationservice.application.service;

import com.orazaka.notification.domain.model.Channel;
import com.orazaka.notification.domain.model.NotificationRequest;
import com.orazaka.notificationservice.domain.exception.DeliveryException;
import com.orazaka.notificationservice.domain.model.Notification;
import com.orazaka.notificationservice.domain.port.DeliveryClient;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Sends a notification through the channel it names: renders the template, then hands the result to
 * the one {@link DeliveryClient} serving that channel.
 *
 * <p>A channel with no configured provider fails the send. The caller (an AMQP listener) lets the
 * failure propagate, so the message is retried and dead-lettered — a notification this deployment
 * cannot deliver is visible in {@code <queue>.dlq}, never dropped in silence (AGENTS.md §12).
 */
@Service
public class NotificationService {

  private static final Logger logger = LoggerFactory.getLogger(NotificationService.class);

  private final NotificationTemplateService templates;
  private final Map<Channel, DeliveryClient> clients;

  public NotificationService(NotificationTemplateService templates, List<DeliveryClient> clients) {
    this.templates = templates;
    Map<Channel, DeliveryClient> byChannel = new EnumMap<>(Channel.class);
    for (DeliveryClient client : clients) {
      if (byChannel.putIfAbsent(client.channel(), client) != null) {
        throw new IllegalStateException("Two delivery clients claim channel " + client.channel());
      }
    }
    this.clients = Map.copyOf(byChannel);
  }

  /**
   * Renders and delivers one request.
   *
   * @param request what to send
   * @throws DeliveryException when the channel is not configured or the provider refused
   */
  public void send(NotificationRequest request) {
    DeliveryClient client = clients.get(request.channel());
    if (client == null || !client.available()) {
      throw new DeliveryException("No provider configured for channel " + request.channel());
    }
    Notification notification = templates.render(request);
    client.deliver(notification);
    logger.info(
        "Delivered notification template={} channel={}", request.template(), request.channel());
  }
}
