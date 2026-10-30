package com.krizaka.notifications.service.infrastructure.adapter.delivery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.krizaka.notifications.domain.model.Channel;
import com.krizaka.notifications.service.domain.exception.DeliveryException;
import com.krizaka.notifications.service.domain.model.Notification;
import com.krizaka.notifications.service.infrastructure.config.NotificationProperties;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

/**
 * krizaka-notifications#26: a recipient that accepts the connection and never answers must fail the
 * delivery within the read timeout instead of holding a worker thread for ever. The adapters are
 * built the way Spring builds them (the two-argument constructor), against a server that hangs.
 */
class DeliveryTimeoutsTest {

  private HttpServer hanging;
  private final CountDownLatch release = new CountDownLatch(1);

  @BeforeEach
  void startHangingServer() throws IOException {
    hanging = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    hanging.createContext(
        "/",
        exchange -> {
          try {
            release.await();
          } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
          }
          exchange.close();
        });
    hanging.start();
  }

  @AfterEach
  void stop() {
    release.countDown();
    hanging.stop(0);
  }

  private String url() {
    return "http://127.0.0.1:" + hanging.getAddress().getPort();
  }

  @Test
  void theTimeoutsAreShort() {
    assertThat(DeliveryHttp.READ_TIMEOUT).isLessThanOrEqualTo(Duration.ofSeconds(15));
    assertThat(DeliveryHttp.CONNECT_TIMEOUT).isLessThanOrEqualTo(Duration.ofSeconds(5));
  }

  @Test
  void aWebhookThatNeverAnswersFailsTheDelivery() {
    NotificationProperties properties =
        new NotificationProperties(
            "no-reply@test",
            null,
            new NotificationProperties.Links("http://x/v?t={token}", "http://x/r?t={token}"),
            null,
            new NotificationProperties.Webhook(List.of("127.0.0.1"), null));
    WebhookDeliveryAdapter adapter = new WebhookDeliveryAdapter(RestClient.builder(), properties);

    long started = System.nanoTime();
    assertThatThrownBy(
            () ->
                adapter.deliver(
                    new Notification(Channel.WEBHOOK, url() + "/hook", "t", "Subject", "Body")))
        .isInstanceOf(DeliveryException.class);

    assertThat(Duration.ofNanos(System.nanoTime() - started))
        .isLessThan(DeliveryHttp.READ_TIMEOUT.plusSeconds(5));
  }

  @Test
  void twilioThatNeverAnswersFailsTheDelivery() {
    NotificationProperties properties =
        new NotificationProperties(
            "no-reply@test",
            null,
            new NotificationProperties.Links("http://x/v?t={token}", "http://x/r?t={token}"),
            new NotificationProperties.Twilio("AC123", "secret", "+15550000000", url()),
            null);
    TwilioSmsDeliveryAdapter adapter =
        new TwilioSmsDeliveryAdapter(RestClient.builder(), properties);

    long started = System.nanoTime();
    assertThatThrownBy(
            () ->
                adapter.deliver(
                    new Notification(Channel.SMS, "+15551234567", "otp", "", "Your code is 1234")))
        .isInstanceOf(DeliveryException.class);

    assertThat(Duration.ofNanos(System.nanoTime() - started))
        .isLessThan(DeliveryHttp.READ_TIMEOUT.plusSeconds(5));
  }
}
