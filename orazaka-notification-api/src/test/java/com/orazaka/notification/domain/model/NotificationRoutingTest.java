package com.orazaka.notification.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class NotificationRoutingTest {

  @Test
  void followsThePlatformEventGrammar() {
    assertThat(NotificationRouting.EVENTS_EXCHANGE).isEqualTo("orazaka.events");
    assertThat(NotificationRouting.NOTIFICATION_REQUESTED).matches("evt\\.[a-z]+\\.[a-z-]+");
  }
}
