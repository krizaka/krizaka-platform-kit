package com.orazaka.notificationservice.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class AmqpConstantsTest {

  @Test
  void keepsTheQueueNamesTheIdentityEventsWereAlwaysBoundTo() {
    assertThat(AmqpConstants.USER_NOTIFICATIONS_QUEUE)
        .isEqualTo("orazaka.events.user.notifications");
    assertThat(AmqpConstants.PASSWORD_NOTIFICATIONS_QUEUE)
        .isEqualTo("orazaka.events.password.notifications");
  }

  @Test
  void deadLettersEveryQueueToItsDlq() {
    assertThat(AmqpConstants.USER_NOTIFICATIONS_DLQ)
        .isEqualTo(AmqpConstants.USER_NOTIFICATIONS_QUEUE + ".dlq");
    assertThat(AmqpConstants.PASSWORD_NOTIFICATIONS_DLQ)
        .isEqualTo(AmqpConstants.PASSWORD_NOTIFICATIONS_QUEUE + ".dlq");
    assertThat(AmqpConstants.REQUESTS_DLQ).isEqualTo(AmqpConstants.REQUESTS_QUEUE + ".dlq");
    assertThat(AmqpConstants.REQUESTS_BINDING).isEqualTo("evt.notification.requested");
  }
}
