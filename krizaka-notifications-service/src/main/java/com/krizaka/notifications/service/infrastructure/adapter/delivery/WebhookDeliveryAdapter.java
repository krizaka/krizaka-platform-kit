package com.krizaka.notifications.service.infrastructure.adapter.delivery;

import com.krizaka.notifications.domain.model.Channel;
import com.krizaka.notifications.service.domain.exception.DeliveryException;
import com.krizaka.notifications.service.domain.model.Notification;
import com.krizaka.notifications.service.domain.port.DeliveryClient;
import com.krizaka.notifications.service.infrastructure.config.NotificationProperties;
import java.net.URI;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * {@link Channel#WEBHOOK}: an HTTP POST of {@code {template, subject, body}} as JSON to the
 * recipient URL.
 *
 * <p>The URL arrives inside a message, so the host must be on {@code
 * krizaka.notifications.webhook.allowed-hosts}; with an empty list the channel is unavailable. A
 * service that POSTs wherever a message tells it to is a request-forgery primitive, and the party
 * who would abuse it is not the one configuring the list (AGENTS.md §12).
 */
@Component
class WebhookDeliveryAdapter implements DeliveryClient {

  private final RestClient restClient;
  private final NotificationProperties.Webhook webhook;

  WebhookDeliveryAdapter(RestClient.Builder builder, NotificationProperties properties) {
    this.restClient = builder.clone().build();
    this.webhook = properties.webhook();
  }

  @Override
  public Channel channel() {
    return Channel.WEBHOOK;
  }

  @Override
  public boolean available() {
    return !webhook.allowedHosts().isEmpty();
  }

  @Override
  public void deliver(Notification notification) {
    URI target = target(notification.recipient());
    try {
      restClient
          .post()
          .uri(target)
          .contentType(MediaType.APPLICATION_JSON)
          .body(
              Map.of(
                  "template", notification.template(),
                  "subject", notification.subject(),
                  "body", notification.body()))
          .retrieve()
          .toBodilessEntity();
    } catch (RestClientException refused) {
      throw new DeliveryException("Webhook delivery failed", refused);
    }
  }

  private URI target(String recipient) {
    URI uri;
    try {
      uri = URI.create(recipient);
    } catch (IllegalArgumentException malformed) {
      throw new DeliveryException("Webhook recipient is not a URL", malformed);
    }
    String scheme = uri.getScheme();
    if (!"https".equalsIgnoreCase(scheme) && !"http".equalsIgnoreCase(scheme)) {
      throw new DeliveryException("Webhook recipient must be an http(s) URL");
    }
    if (!webhook.allows(uri.getHost())) {
      throw new DeliveryException("Webhook host is not on the allow-list");
    }
    return uri;
  }
}
