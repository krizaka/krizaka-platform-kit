package com.krizaka.notifications.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.krizaka.notifications.service.application.service.NotificationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/** The context wires with no broker: listener containers do not start in this test. */
@SpringBootTest(
    properties = {
      "spring.rabbitmq.listener.simple.auto-startup=false",
      "management.otlp.tracing.export.enabled=false",
      "logging.file.name="
    })
class NotificationServiceApplicationTest {

  @Autowired private NotificationService notificationService;

  @Test
  void contextLoads() {
    assertThat(notificationService).isNotNull();
  }
}
