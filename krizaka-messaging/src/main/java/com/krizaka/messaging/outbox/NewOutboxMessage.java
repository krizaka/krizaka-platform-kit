package com.krizaka.messaging.outbox;

import java.util.Arrays;
import java.util.Map;
import java.util.Objects;

/**
 * A row to append to the outbox: everything the relay will publish, decided at write time.
 *
 * <p>The {@code messageId} is chosen <b>here</b>, when the row is written, not when it is
 * published: a relay that crashes between the publish and the commit republishes the row with the
 * same id, and the consumers' {@link com.krizaka.messaging.dedup.MessageDedup} drops the copy.
 *
 * @param exchange the exchange to publish to
 * @param routingKey the routing key
 * @param messageId the AMQP {@code messageId} consumers deduplicate on
 * @param body the serialized payload (JSON), published as-is
 * @param headers the AMQP headers the message will carry (string values only, so that any column
 *     type — {@code jsonb}, {@code text} — can store them)
 */
public record NewOutboxMessage(
    String exchange,
    String routingKey,
    String messageId,
    byte[] body,
    Map<String, String> headers) {

  /** Validates the row and takes defensive copies of the body and the headers. */
  public NewOutboxMessage {
    requireText(exchange, "exchange");
    requireText(routingKey, "routingKey");
    requireText(messageId, "messageId");
    body = Objects.requireNonNull(body, "body").clone();
    headers = headers == null ? Map.of() : Map.copyOf(headers);
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
    return other instanceof NewOutboxMessage that
        && exchange.equals(that.exchange)
        && routingKey.equals(that.routingKey)
        && messageId.equals(that.messageId)
        && Arrays.equals(body, that.body)
        && headers.equals(that.headers);
  }

  @Override
  public int hashCode() {
    return Objects.hash(exchange, routingKey, messageId, Arrays.hashCode(body), headers);
  }

  @Override
  public String toString() {
    return "NewOutboxMessage[exchange=%s, routingKey=%s, messageId=%s, %d bytes, headers=%s]"
        .formatted(exchange, routingKey, messageId, body.length, headers);
  }

  private static void requireText(String value, String name) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(name + " is required");
    }
  }
}
