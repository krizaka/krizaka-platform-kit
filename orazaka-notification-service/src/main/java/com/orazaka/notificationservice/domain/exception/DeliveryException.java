package com.orazaka.notificationservice.domain.exception;

/**
 * A notification could not be delivered: no provider is configured for its channel, the recipient
 * is not acceptable for it, or the provider refused it. Thrown out of the listener so the message
 * is retried and finally dead-lettered — never swallowed.
 */
public class DeliveryException extends RuntimeException {

  public DeliveryException(String message) {
    super(message);
  }

  public DeliveryException(String message, Throwable cause) {
    super(message, cause);
  }
}
