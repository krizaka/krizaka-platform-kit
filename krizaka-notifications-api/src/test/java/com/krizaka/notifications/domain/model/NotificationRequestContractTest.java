package com.krizaka.notifications.domain.model;

import com.krizaka.test.events.EventContractTest;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** {@link NotificationRequest}, as a requesting service publishes it, conforms to its schema. */
class NotificationRequestContractTest extends EventContractTest {

  @Test
  void anEmailRequestConforms() {
    assertConforms(
        NotificationRouting.NOTIFICATION_REQUESTED,
        1,
        new NotificationRequest(
            Channel.EMAIL, "ada@example.com", "welcome", "fr-FR", Map.of("name", "Ada")));
  }

  @Test
  void aRequestWithoutLocaleOrVariablesConforms() {
    assertConforms(
        NotificationRouting.NOTIFICATION_REQUESTED,
        1,
        new NotificationRequest(
            Channel.WEBHOOK, "https://example.com/hook", "job-done", null, null));
  }
}
