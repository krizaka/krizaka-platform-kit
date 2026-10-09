package com.krizaka.messaging.event;

import com.krizaka.messaging.outbox.NewOutboxMessage;
import com.krizaka.messaging.outbox.OutboxStore;
import com.krizaka.messaging.topology.MessagingExchanges;
import java.time.Clock;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.slf4j.MDC;
import tools.jackson.databind.json.JsonMapper;

/**
 * The {@link EventPublisher} over an {@link OutboxStore}: serializes the event with the
 * application's {@link JsonMapper}, fills the envelope ({@link EventHeaders}) and appends one row
 * to the events exchange.
 *
 * <p>The {@code messageId} is a random UUID chosen here, at write time, so a row the relay
 * publishes twice (a crash between the publish and the commit) reaches consumers twice with the
 * <i>same</i> id, and their {@link com.krizaka.messaging.dedup.MessageDedup} drops the copy.
 *
 * <p>The correlation id is the current {@value #CORRELATION_MDC_KEY} of the logging MDC — the one
 * krizaka-web's {@code CorrelationId} opens for every HTTP request — or a new one when the event is
 * not produced under a request.
 */
public final class OutboxEventPublisher implements EventPublisher {

  /** The MDC key the correlation id is read from (krizaka-web's {@code CorrelationId.MDC_KEY}). */
  public static final String CORRELATION_MDC_KEY = "requestId";

  private final OutboxStore store;
  private final JsonMapper json;
  private final String exchange;
  private final String producer;
  private final Clock clock;

  /**
   * A publisher writing to one outbox.
   *
   * @param store the outbox of the current context
   * @param json the application's mapper, so an event is written as the HTTP API would write it
   * @param exchanges where events are published — the events exchange
   * @param producer the name of this service ({@code krizaka.messaging.producer})
   * @param clock the source of {@link EventHeaders#OCCURRED_AT}
   */
  public OutboxEventPublisher(
      OutboxStore store,
      JsonMapper json,
      MessagingExchanges exchanges,
      String producer,
      Clock clock) {
    this.store = Objects.requireNonNull(store, "store");
    this.json = Objects.requireNonNull(json, "json");
    this.exchange = Objects.requireNonNull(exchanges, "exchanges").events();
    if (producer == null || producer.isBlank()) {
      throw new IllegalArgumentException("producer is required");
    }
    this.producer = producer;
    this.clock = Objects.requireNonNull(clock, "clock");
  }

  @Override
  public void publish(String routingKey, int version, Object event) {
    if (routingKey == null || routingKey.isBlank()) {
      throw new IllegalArgumentException("routingKey is required");
    }
    if (version < 1) {
      throw new IllegalArgumentException("version starts at 1, was " + version);
    }
    Objects.requireNonNull(event, "event");
    Map<String, String> headers =
        Map.of(
            EventHeaders.TYPE, routingKey,
            EventHeaders.VERSION, Integer.toString(version),
            EventHeaders.PRODUCER, producer,
            EventHeaders.CORRELATION, correlationId(),
            EventHeaders.OCCURRED_AT, clock.instant().toString());
    store.append(
        new NewOutboxMessage(
            exchange,
            routingKey,
            UUID.randomUUID().toString(),
            json.writeValueAsBytes(event),
            headers));
  }

  private static String correlationId() {
    String current = MDC.get(CORRELATION_MDC_KEY);
    return current != null && !current.isBlank() ? current : UUID.randomUUID().toString();
  }
}
