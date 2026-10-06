package com.orazaka.notificationservice.infrastructure.config;

import com.orazaka.notification.domain.model.NotificationRouting;

/**
 * AMQP topology of the notification service (AGENTS.md §6). The identity queues keep the names the
 * platform has always bound — this service took them over from the automation service — so the
 * topology a running broker already holds stays valid.
 */
public final class AmqpConstants {

  // ── Exchanges ────────────────────────────────────────────────────────────
  public static final String EVENTS_EXCHANGE = NotificationRouting.EVENTS_EXCHANGE;
  public static final String DLX_EXCHANGE = "orazaka.dlx";

  // ── Identity events (consumed) — evt.{aggregate}.{type} ──────────────────
  public static final String USER_NOTIFICATIONS_QUEUE = "orazaka.events.user.notifications";
  public static final String USER_NOTIFICATIONS_BINDING = "evt.user.*";
  public static final String USER_NOTIFICATIONS_DLQ = USER_NOTIFICATIONS_QUEUE + ".dlq";

  public static final String PASSWORD_NOTIFICATIONS_QUEUE = "orazaka.events.password.notifications";
  public static final String PASSWORD_NOTIFICATIONS_BINDING = "evt.password.*";
  public static final String PASSWORD_NOTIFICATIONS_DLQ = PASSWORD_NOTIFICATIONS_QUEUE + ".dlq";

  // ── Explicit requests (consumed) ─────────────────────────────────────────
  public static final String REQUESTS_QUEUE = "orazaka.notifications.requests";
  public static final String REQUESTS_BINDING = NotificationRouting.NOTIFICATION_REQUESTED;
  public static final String REQUESTS_DLQ = REQUESTS_QUEUE + ".dlq";

  private AmqpConstants() {}
}
