package com.krizaka.notifications.domain.model;

/**
 * The AMQP coordinates of the notification contract. A producer publishes a {@link
 * NotificationRequest} as JSON, with a {@code messageId}, to the events exchange of the platform it
 * runs on ({@code krizaka.messaging.exchanges.events}, {@code krizaka.events} by default) with the
 * routing key {@link #NOTIFICATION_REQUESTED}.
 */
public final class NotificationRouting {

  /** Routing key of an explicit {@link NotificationRequest}. */
  public static final String NOTIFICATION_REQUESTED = "evt.notification.requested";

  private NotificationRouting() {}
}
