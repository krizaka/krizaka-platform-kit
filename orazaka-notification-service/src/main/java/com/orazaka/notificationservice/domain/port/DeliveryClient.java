package com.orazaka.notificationservice.domain.port;

import com.orazaka.notification.domain.model.Channel;
import com.orazaka.notificationservice.domain.model.Notification;

/**
 * Outbound port of one delivery channel — the SPI a new channel implements (as a package-private
 * Spring bean in {@code infrastructure/adapter/delivery}). Exactly one client serves each {@link
 * Channel}.
 */
public interface DeliveryClient {

  /** The channel this client delivers. */
  Channel channel();

  /**
   * Whether this deployment configured the provider behind the channel. A client that is not
   * available is never called: the notification fails and is dead-lettered instead of being
   * silently dropped.
   */
  boolean available();

  /**
   * Delivers one rendered notification.
   *
   * @param notification what to send
   * @throws com.orazaka.notificationservice.domain.exception.DeliveryException when the provider
   *     refused or could not be reached
   */
  void deliver(Notification notification);
}
