package com.krizaka.notifications.service.infrastructure.adapter.delivery;

import com.krizaka.notifications.domain.model.Channel;
import com.krizaka.notifications.service.domain.exception.DeliveryException;
import com.krizaka.notifications.service.domain.model.Notification;
import com.krizaka.notifications.service.domain.port.DeliveryClient;
import com.krizaka.notifications.service.infrastructure.config.NotificationProperties;
import java.net.URI;
import java.time.Clock;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.databind.json.JsonMapper;

/**
 * {@link Channel#WEBHOOK}: an HTTP POST of {@code {template, subject, body}} as JSON to the
 * recipient URL, signed (Standard Webhooks) when {@code
 * krizaka.notifications.webhook.signing-secret} is set.
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
  private final Clock clock;
  private final JsonMapper json = JsonMapper.builder().build();

  @org.springframework.beans.factory.annotation.Autowired
  WebhookDeliveryAdapter(RestClient.Builder builder, NotificationProperties properties) {
    this(builder, properties, Clock.systemUTC());
  }

  WebhookDeliveryAdapter(
      RestClient.Builder builder, NotificationProperties properties, Clock clock) {
    this.restClient = builder.clone().build();
    this.webhook = properties.webhook();
    this.clock = clock;
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
    String body =
        json.writeValueAsString(
            Map.of(
                "template", notification.template(),
                "subject", notification.subject(),
                "body", notification.body()));
    try {
      RestClient.RequestBodySpec request =
          restClient.post().uri(target).contentType(MediaType.APPLICATION_JSON);
      if (webhook.signed()) {
        String id = "msg_" + UUID.randomUUID();
        long timestamp = clock.instant().getEpochSecond();
        request =
            request
                .header(WebhookSigner.ID_HEADER, id)
                .header(WebhookSigner.TIMESTAMP_HEADER, Long.toString(timestamp))
                .header(
                    WebhookSigner.SIGNATURE_HEADER,
                    WebhookSigner.sign(webhook.signingSecret(), id, timestamp, body));
      }
      request.body(body).retrieve().toBodilessEntity();
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
