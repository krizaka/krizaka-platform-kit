package com.krizaka.notifications.service.infrastructure.adapter.delivery;

import java.time.Duration;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;

/**
 * The timeouts of every outbound delivery call (webhook recipients, Twilio). A recipient that
 * accepts the connection and never answers must cost one delivery attempt, not a worker thread for
 * ever: the failure is recorded and the outbox retries it later.
 */
final class DeliveryHttp {

  // @fixed: protocol safety bounds for third-party endpoints, not business parameters
  static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(3);
  static final Duration READ_TIMEOUT = Duration.ofSeconds(10);

  private DeliveryHttp() {}

  static ClientHttpRequestFactory timeouts() {
    SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
    factory.setConnectTimeout(CONNECT_TIMEOUT);
    factory.setReadTimeout(READ_TIMEOUT);
    return factory;
  }
}
