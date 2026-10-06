package com.orazaka.notificationservice.infrastructure.adapter.delivery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withBadRequest;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.orazaka.notification.domain.model.Channel;
import com.orazaka.notificationservice.domain.exception.DeliveryException;
import com.orazaka.notificationservice.domain.model.Notification;
import com.orazaka.notificationservice.infrastructure.config.NotificationProperties;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class TwilioSmsDeliveryAdapterTest {

  private static final Notification SMS =
      new Notification(Channel.SMS, "+15551234567", "otp-code", "", "Your code is 1234");

  private static NotificationProperties properties(String sid) {
    return new NotificationProperties(
        "no-reply@test",
        null,
        new NotificationProperties.Links("http://x/v?t={token}", "http://x/r?t={token}"),
        new NotificationProperties.Twilio(sid, "secret", "+15550000000", "https://twilio.test"),
        null);
  }

  @Test
  void postsTheMessageToTheTwilioApi() {
    RestClient.Builder builder = RestClient.builder();
    MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    server
        .expect(requestTo("https://twilio.test/2010-04-01/Accounts/AC123/Messages.json"))
        .andExpect(method(HttpMethod.POST))
        .andExpect(header("Authorization", "Basic QUMxMjM6c2VjcmV0"))
        .andExpect(
            content()
                .formDataContains(java.util.Map.of("To", "+15551234567", "From", "+15550000000")))
        .andRespond(withSuccess());

    TwilioSmsDeliveryAdapter adapter = new TwilioSmsDeliveryAdapter(builder, properties("AC123"));
    adapter.deliver(SMS);

    server.verify();
    assertThat(adapter.available()).isTrue();
    assertThat(adapter.channel()).isEqualTo(Channel.SMS);
  }

  @Test
  void isUnavailableWithoutCredentials() {
    assertThat(new TwilioSmsDeliveryAdapter(RestClient.builder(), properties("")).available())
        .isFalse();
  }

  @Test
  void refusesANonE164RecipientAndTranslatesAProviderRefusal() {
    RestClient.Builder builder = RestClient.builder();
    MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    server
        .expect(requestTo("https://twilio.test/2010-04-01/Accounts/AC123/Messages.json"))
        .andRespond(withBadRequest());
    TwilioSmsDeliveryAdapter adapter = new TwilioSmsDeliveryAdapter(builder, properties("AC123"));

    assertThatThrownBy(() -> adapter.deliver(new Notification(Channel.SMS, "0555", "otp", "", "x")))
        .isInstanceOf(DeliveryException.class);
    assertThatThrownBy(() -> adapter.deliver(SMS)).isInstanceOf(DeliveryException.class);
  }
}
