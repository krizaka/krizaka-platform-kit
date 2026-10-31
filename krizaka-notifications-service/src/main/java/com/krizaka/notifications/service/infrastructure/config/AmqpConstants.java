package com.krizaka.notifications.service.infrastructure.config;

import com.krizaka.notifications.domain.model.NotificationRouting;

/**
 * The queues this service owns, and what each one binds. The exchanges are not here: they belong to
 * the platform the service runs on and come from {@code krizaka.messaging.exchanges}
 * (krizaka-messaging).
 */
public final class AmqpConstants {

  // ── Identity events (consumed) — evt.{aggregate}.{type} ──────────────────
  public static final String USER_NOTIFICATIONS_QUEUE = "krizaka.notifications.user-events";
  public static final String USER_NOTIFICATIONS_BINDING = "evt.user.*";
  public static final String USER_NOTIFICATIONS_DLQ = USER_NOTIFICATIONS_QUEUE + ".dlq";

  public static final String PASSWORD_NOTIFICATIONS_QUEUE = "krizaka.notifications.password-events";
  public static final String PASSWORD_NOTIFICATIONS_BINDING = "evt.password.*";
  public static final String PASSWORD_NOTIFICATIONS_DLQ = PASSWORD_NOTIFICATIONS_QUEUE + ".dlq";

  // ── Explicit requests (consumed) ─────────────────────────────────────────
  public static final String REQUESTS_QUEUE = "krizaka.notifications.requests";
  public static final String REQUESTS_BINDING = NotificationRouting.NOTIFICATION_REQUESTED;
  public static final String REQUESTS_DLQ = REQUESTS_QUEUE + ".dlq";

  private AmqpConstants() {}
}
