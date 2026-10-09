package com.krizaka.messaging.event;

/**
 * Publishes a domain event — the one way to: transactional, through the outbox, idempotent
 * downstream.
 *
 * <pre>{@code
 * @Transactional
 * public void register(NewUser user) {
 *   users.save(user);
 *   events.publish("evt.user.registered", 1, new UserRegistered(user.id(), user.email()));
 * }
 * }</pre>
 *
 * <p>Call it inside the transaction that writes the state change: the event is a row of that
 * transaction, committed or rolled back with it, and the outbox relay publishes it after the commit
 * — never an event for a change that did not happen, never a change whose event is lost.
 */
public interface EventPublisher {

  /**
   * Records an event in the current context's outbox, in the current transaction.
   *
   * @param routingKey the event type, {@code evt.<aggregate>.<event>} (the routing key on the
   *     events exchange)
   * @param version the contract version of {@code event}, from 1
   * @param event the event, serialized to JSON as the message body
   */
  void publish(String routingKey, int version, Object event);
}
