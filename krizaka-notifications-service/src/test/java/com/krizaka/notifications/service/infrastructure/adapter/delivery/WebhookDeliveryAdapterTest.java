package com.krizaka.notifications.service.infrastructure.adapter.delivery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.krizaka.notifications.domain.model.Channel;
import com.krizaka.notifications.service.domain.exception.DeliveryException;
import com.krizaka.notifications.service.domain.model.Notification;
import com.krizaka.notifications.service.infrastructure.config.NotificationProperties;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class WebhookDeliveryAdapterTest {

  private static NotificationProperties properties(List<String> hosts) {
    return new NotificationProperties(
        "no-reply@test",
        null,
        new NotificationProperties.Links("http://x/v?t={token}", "http://x/r?t={token}"),
        null,
        new NotificationProperties.Webhook(hosts));
  }

  private static Notification to(String url) {
    return new Notification(Channel.WEBHOOK, url, "build-finished", "Done", "All green");
  }

  @Test
  void postsJsonToAnAllowedHost() {
    RestClient.Builder builder = RestClient.builder();
    MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    server
        .expect(requestTo("https://hooks.example.com/notify"))
        .andExpect(method(HttpMethod.POST))
        .andExpect(
            content()
                .json(
                    "{\"template\":\"build-finished\",\"subject\":\"Done\",\"body\":\"All green\"}"))
        .andRespond(withSuccess());

    WebhookDeliveryAdapter adapter =
        new WebhookDeliveryAdapter(builder, properties(List.of("hooks.example.com")));
    adapter.deliver(to("https://hooks.example.com/notify"));

    server.verify();
    assertThat(adapter.available()).isTrue();
    assertThat(adapter.channel()).isEqualTo(Channel.WEBHOOK);
  }

  @Test
  void neverCallsAHostOffTheAllowList() {
    WebhookDeliveryAdapter adapter =
        new WebhookDeliveryAdapter(RestClient.builder(), properties(List.of("hooks.example.com")));

    assertThatThrownBy(() -> adapter.deliver(to("http://169.254.169.254/latest/meta-data")))
        .isInstanceOf(DeliveryException.class);
    assertThatThrownBy(() -> adapter.deliver(to("file:///etc/passwd")))
        .isInstanceOf(DeliveryException.class);
    assertThatThrownBy(() -> adapter.deliver(to("not a url")))
        .isInstanceOf(DeliveryException.class);
  }

  @Test
  void isUnavailableWithAnEmptyAllowList() {
    assertThat(new WebhookDeliveryAdapter(RestClient.builder(), properties(List.of())).available())
        .isFalse();
  }
}
