package com.krizaka.notifications.service.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.krizaka.notifications.domain.model.Channel;
import com.krizaka.notifications.domain.model.NotificationRequest;
import com.krizaka.notifications.service.domain.exception.DeliveryException;
import com.krizaka.notifications.service.domain.model.Notification;
import com.krizaka.notifications.service.domain.port.DeliveryClient;
import com.krizaka.notifications.service.infrastructure.config.NotificationProperties;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;

class NotificationServiceTest {

  private static final NotificationTemplateService TEMPLATES =
      new NotificationTemplateService(
          new DefaultResourceLoader(),
          new NotificationProperties(
              "no-reply@test",
              null,
              new NotificationProperties.Links("http://x/v?t={token}", "http://x/r?t={token}"),
              null,
              null));

  private static final class RecordingClient implements DeliveryClient {
    private final Channel channel;
    private final boolean available;
    private final List<Notification> delivered = new ArrayList<>();

    RecordingClient(Channel channel, boolean available) {
      this.channel = channel;
      this.available = available;
    }

    @Override
    public Channel channel() {
      return channel;
    }

    @Override
    public boolean available() {
      return available;
    }

    @Override
    public void deliver(Notification notification) {
      delivered.add(notification);
    }
  }

  private static NotificationRequest resetEmail() {
    return new NotificationRequest(
        Channel.EMAIL, "a@b.c", "password-reset", "en", Map.of("resetUrl", "https://r"));
  }

  @Test
  void deliversThroughTheClientOfTheRequestedChannel() {
    RecordingClient email = new RecordingClient(Channel.EMAIL, true);
    RecordingClient sms = new RecordingClient(Channel.SMS, true);

    new NotificationService(TEMPLATES, List.of(email, sms)).send(resetEmail());

    assertThat(email.delivered)
        .singleElement()
        .satisfies(n -> assertThat(n.body()).contains("https://r"));
    assertThat(sms.delivered).isEmpty();
  }

  @Test
  void failsWhenTheChannelHasNoConfiguredProvider() {
    RecordingClient unconfigured = new RecordingClient(Channel.EMAIL, false);
    NotificationService service = new NotificationService(TEMPLATES, List.of(unconfigured));

    assertThatThrownBy(() -> service.send(resetEmail())).isInstanceOf(DeliveryException.class);
    assertThatThrownBy(() -> new NotificationService(TEMPLATES, List.of()).send(resetEmail()))
        .isInstanceOf(DeliveryException.class);
    assertThat(unconfigured.delivered).isEmpty();
  }

  @Test
  void refusesTwoClientsForOneChannel() {
    List<DeliveryClient> clients =
        List.of(new RecordingClient(Channel.SMS, true), new RecordingClient(Channel.SMS, true));

    assertThatThrownBy(() -> new NotificationService(TEMPLATES, clients))
        .isInstanceOf(IllegalStateException.class);
  }
}
