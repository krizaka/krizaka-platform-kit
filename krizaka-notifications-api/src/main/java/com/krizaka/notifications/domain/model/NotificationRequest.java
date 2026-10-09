package com.krizaka.notifications.domain.model;

import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * What a producer asks the notification service to deliver: a template rendered for a locale and
 * sent through one channel to one recipient. The producer never renders text and never knows the
 * provider; it declares the intent and the variables.
 *
 * <p>Published on the platform's events exchange with routing key {@link
 * NotificationRouting#NOTIFICATION_REQUESTED}. Set an AMQP {@code messageId} to make the delivery
 * idempotent.
 *
 * @param channel the delivery medium
 * @param recipient address, phone number or URL — read according to {@code channel}
 * @param template template key, lower-kebab-case (e.g. {@code verify-email})
 * @param locale BCP-47 language tag; defaults to {@code en}
 * @param variables values substituted into the template ({@code {{name}}}); never {@code null}
 */
public record NotificationRequest(
    Channel channel,
    String recipient,
    String template,
    String locale,
    Map<String, String> variables) {

  private static final Pattern TEMPLATE_KEY = Pattern.compile("[a-z0-9]+(?:-[a-z0-9]+)*");

  /** Compact constructor: the request validates itself, so no consumer re-checks it. */
  public NotificationRequest {
    Objects.requireNonNull(channel, "channel is required");
    if (recipient == null || recipient.isBlank()) {
      throw new IllegalArgumentException("recipient is required");
    }
    if (template == null || !TEMPLATE_KEY.matcher(template).matches()) {
      throw new IllegalArgumentException("template must be a lower-kebab-case key: " + template);
    }
    locale =
        (locale == null || locale.isBlank())
            ? "en"
            : Locale.forLanguageTag(locale.strip()).getLanguage();
    if (locale.isEmpty()) {
      locale = "en";
    }
    variables = (variables == null) ? Map.of() : Map.copyOf(variables);
    recipient = recipient.strip();
  }
}
