package com.krizaka.notifications.service.infrastructure.adapter.delivery;

import com.krizaka.notifications.domain.model.Channel;
import com.krizaka.notifications.service.domain.exception.DeliveryException;
import com.krizaka.notifications.service.domain.model.Notification;
import com.krizaka.notifications.service.domain.port.DeliveryClient;
import com.krizaka.notifications.service.infrastructure.config.NotificationProperties;
import java.util.regex.Pattern;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * {@link Channel#SMS} through the Twilio Messages API ({@code POST
 * /2010-04-01/Accounts/{sid}/Messages.json}). Available only when the account SID, auth token and
 * sending number are all configured.
 */
@Component
class TwilioSmsDeliveryAdapter implements DeliveryClient {

  private static final Pattern E164 = Pattern.compile("\\+[1-9]\\d{6,14}");

  private final RestClient restClient;
  private final NotificationProperties.Twilio twilio;

  @org.springframework.beans.factory.annotation.Autowired
  TwilioSmsDeliveryAdapter(RestClient.Builder builder, NotificationProperties properties) {
    this(builder, properties, DeliveryHttp.timeouts());
  }

  /** {@code requestFactory} {@code null}: the builder's own (a test binds a mock server to it). */
  TwilioSmsDeliveryAdapter(
      RestClient.Builder builder,
      NotificationProperties properties,
      ClientHttpRequestFactory requestFactory) {
    this.twilio = properties.twilio();
    RestClient.Builder client = builder.clone();
    if (requestFactory != null) {
      client.requestFactory(requestFactory);
    }
    this.restClient =
        client
            .baseUrl(twilio.baseUrl())
            .defaultHeaders(
                headers -> {
                  if (twilio.configured()) {
                    headers.setBasicAuth(twilio.accountSid(), twilio.authToken());
                  }
                })
            .build();
  }

  @Override
  public Channel channel() {
    return Channel.SMS;
  }

  @Override
  public boolean available() {
    return twilio.configured();
  }

  @Override
  public void deliver(Notification notification) {
    if (!E164.matcher(notification.recipient()).matches()) {
      throw new DeliveryException("SMS recipient is not an E.164 number");
    }
    MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
    form.add("To", notification.recipient());
    form.add("From", twilio.fromNumber());
    form.add("Body", notification.body());
    try {
      restClient
          .post()
          .uri("/2010-04-01/Accounts/{sid}/Messages.json", twilio.accountSid())
          .contentType(MediaType.APPLICATION_FORM_URLENCODED)
          .body(form)
          .retrieve()
          .toBodilessEntity();
    } catch (RestClientException refused) {
      throw new DeliveryException("Twilio refused the SMS", refused);
    }
  }
}
