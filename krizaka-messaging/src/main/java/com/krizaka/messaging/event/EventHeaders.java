package com.krizaka.messaging.event;

/**
 * The event envelope, carried in AMQP headers so that the body stays the bare event.
 *
 * <p>Consumers that read only the body — tolerant copies of the producer's contract — see no
 * change; consumers that want the envelope read these headers. Every value is a string.
 *
 * <pre>{@code
 * kz-type:           evt.user.registered
 * kz-version:        1
 * kz-producer:       krizaka-users
 * kz-correlation-id: 0b6c3f1e-6a52-4c4e-9a43-61f0f1f2a1d7
 * kz-occurred-at:    2026-10-09T08:15:30.123Z
 * }</pre>
 */
public final class EventHeaders {

  /**
   * The event type: its routing key ({@code evt.<aggregate>.<event>}, {@code .v2} and up for an
   * incompatible version).
   */
  public static final String TYPE = "kz-type";

  /**
   * The contract version of the body, from 1. An incompatible change is a new version <i>and</i> a
   * new routing key ({@code evt.user.registered.v2}), so existing consumers never receive it.
   */
  public static final String VERSION = "kz-version";

  /** The service that produced the event ({@code krizaka.messaging.producer}). */
  public static final String PRODUCER = "kz-producer";

  /**
   * The correlation id of the request or job that produced the event — the {@code requestId} of its
   * logs.
   */
  public static final String CORRELATION = "kz-correlation-id";

  /** When the event happened, ISO-8601 in UTC — not when it was published. */
  public static final String OCCURRED_AT = "kz-occurred-at";

  private EventHeaders() {}
}
