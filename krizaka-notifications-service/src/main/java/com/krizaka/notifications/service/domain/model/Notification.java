package com.krizaka.notifications.service.domain.model;

import com.krizaka.notifications.domain.model.Channel;
import java.util.Objects;

/**
 * A rendered notification, ready for a delivery adapter: the template has been resolved for the
 * recipient's locale and every variable substituted. Adapters only transport it.
 *
 * @param channel the delivery medium
 * @param recipient address, phone number or URL — read according to {@code channel}
 * @param template the template key it was rendered from (for traceability, never content)
 * @param subject the subject line (e-mail subject, webhook {@code subject} field)
 * @param body the rendered text
 */
public record Notification(
    Channel channel, String recipient, String template, String subject, String body) {

  public Notification {
    Objects.requireNonNull(channel, "channel is required");
    Objects.requireNonNull(template, "template is required");
    if (recipient == null || recipient.isBlank()) {
      throw new IllegalArgumentException("recipient is required");
    }
    subject = (subject == null) ? "" : subject.strip();
    if (body == null || body.isBlank()) {
      throw new IllegalArgumentException("a notification needs a body");
    }
  }
}
