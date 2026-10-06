package com.orazaka.notification.domain.model;

/**
 * The AMQP coordinates of the notification contract (AGENTS.md §6). A producer needs nothing else:
 * publish a {@link NotificationRequest} to {@link #EVENTS_EXCHANGE} with {@link
 * #NOTIFICATION_REQUESTED}.
 */
public final class NotificationRouting {

  /** The platform's topic exchange for domain events. */
  public static final String EVENTS_EXCHANGE = "orazaka.events";

  /** Routing key of an explicit {@link NotificationRequest}. */
  public static final String NOTIFICATION_REQUESTED = "evt.notification.requested";

  private NotificationRouting() {}
}
