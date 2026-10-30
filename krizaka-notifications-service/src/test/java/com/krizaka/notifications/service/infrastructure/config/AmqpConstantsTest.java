package com.krizaka.notifications.service.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class AmqpConstantsTest {

  @Test
  void ownsItsQueuesUnderItsOwnName() {
    assertThat(AmqpConstants.USER_NOTIFICATIONS_QUEUE)
        .isEqualTo("krizaka.notifications.user-events");
    assertThat(AmqpConstants.PASSWORD_NOTIFICATIONS_QUEUE)
        .isEqualTo("krizaka.notifications.password-events");
    assertThat(AmqpConstants.REQUESTS_QUEUE).isEqualTo("krizaka.notifications.requests");
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
