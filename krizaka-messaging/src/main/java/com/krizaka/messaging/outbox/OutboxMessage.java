package com.krizaka.messaging.outbox;

import java.util.Arrays;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * One pending outbox row, as the relay needs it to publish.
 *
 * @param id the row's identity, handed back to {@link OutboxStore#markPublished} or {@link
 *     OutboxStore#recordFailure}
 * @param exchange the exchange to publish to
 * @param routingKey the routing key
 * @param messageId the AMQP {@code messageId} consumers deduplicate on; {@code null} publishes
 *     without one
 * @param body the serialized payload (JSON), published as-is
 * @param attempts how many publishes of this row have failed so far
 * @param headers the AMQP headers the message carries — the event envelope ({@link
 *     com.krizaka.messaging.event.EventHeaders}) for a row written by an {@link
 *     com.krizaka.messaging.event.EventPublisher}; empty for a row that has none
 */
public record OutboxMessage(
    UUID id,
    String exchange,
    String routingKey,
    String messageId,
    byte[] body,
    int attempts,
    Map<String, String> headers) {

  /** Validates the row and takes defensive copies of the body and the headers. */
  public OutboxMessage {
    Objects.requireNonNull(id, "id");
    Objects.requireNonNull(exchange, "exchange");
    Objects.requireNonNull(routingKey, "routingKey");
    body = Objects.requireNonNull(body, "body").clone();
    if (attempts < 0) {
      throw new IllegalArgumentException("attempts cannot be negative");
    }
    headers = headers == null ? Map.of() : Map.copyOf(headers);
  }

  /**
   * A row without headers — what an outbox written before the event envelope existed holds.
   *
   * @param id the row's identity
   * @param exchange the exchange to publish to
   * @param routingKey the routing key
   * @param messageId the AMQP {@code messageId}, or {@code null}
   * @param body the serialized payload (JSON)
   * @param attempts how many publishes of this row have failed so far
   */
  public OutboxMessage(
      UUID id, String exchange, String routingKey, String messageId, byte[] body, int attempts) {
    this(id, exchange, routingKey, messageId, body, attempts, Map.of());
  }

  /**
   * The payload.
   *
   * @return a copy of the serialized body
   */
  @Override
  public byte[] body() {
    return body.clone();
  }

  @Override
  public boolean equals(Object other) {
    return other instanceof OutboxMessage that
        && id.equals(that.id)
        && exchange.equals(that.exchange)
        && routingKey.equals(that.routingKey)
        && Objects.equals(messageId, that.messageId)
        && Arrays.equals(body, that.body)
        && attempts == that.attempts
        && headers.equals(that.headers);
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        id, exchange, routingKey, messageId, Arrays.hashCode(body), attempts, headers);
  }

  @Override
  public String toString() {
    return ("OutboxMessage[id=%s, exchange=%s, routingKey=%s, messageId=%s, %d bytes, attempts=%d,"
            + " headers=%s]")
        .formatted(id, exchange, routingKey, messageId, body.length, attempts, headers);
  }
}
