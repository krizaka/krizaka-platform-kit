package com.krizaka.notifications.domain.model;

/**
 * The medium a notification is delivered through. The recipient of a {@link NotificationRequest} is
 * read according to it: an e-mail address, an E.164 phone number, or an HTTPS URL.
 */
public enum Channel {
  /** E-mail; the recipient is an address. */
  EMAIL,
  /** Text message; the recipient is an E.164 phone number ({@code +15551234567}). */
  SMS,
  /** HTTP POST of a JSON payload; the recipient is the target URL. */
  WEBHOOK
}
