package com.krizaka.messaging.topology;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Declarable;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.ExchangeBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;

/**
 * A consumer's queue and its dead-letter queue, declared by convention in one line.
 *
 * <pre>{@code
 * @Bean
 * Declarables userEvents(MessagingExchanges exchanges) {
 *   return KrizakaQueues.consumer(exchanges, "krizaka.notifications.user-events",
 *       "evt.user.registered", "evt.user.verified");
 * }
 * }</pre>
 *
 * <p>Declares, idempotently: the events exchange (topic) and the dead-letter exchange (direct)
 * named by {@link MessagingExchanges}; the queue {@code <queue>}, a durable <b>quorum</b> queue
 * bound to the events exchange by each routing key, whose rejected messages dead-letter to {@code
 * <queue>.dlq}; and {@code <queue>.dlq}, a quorum queue bound to the dead-letter exchange by its
 * own name. The kit's listener container factory ({@code KrizakaRabbitAutoConfiguration}) sends a
 * message that exhausted its retries to the same {@code <queue>.dlq} routing key.
 *
 * <p>Queue names follow {@code <service>.<usage>} ({@code krizaka.notifications.user-events}).
 * RabbitMQ refuses to redeclare an existing queue with other arguments: a queue already declared as
 * a classic queue keeps its declaration until it is migrated (drained, deleted, redeclared).
 */
public final class KrizakaQueues {

  /** The suffix of a consumer queue's dead-letter queue. */
  public static final String DLQ_SUFFIX = ".dlq";

  private KrizakaQueues() {}

  /**
   * The declarations of one consumer queue.
   *
   * @param exchanges the events and dead-letter exchanges
   * @param queue the queue name, {@code <service>.<usage>}
   * @param routingKeys the event types the queue receives (topic patterns allowed); at least one
   * @return the exchanges, both queues and their bindings
   */
  public static Declarables consumer(
      MessagingExchanges exchanges, String queue, String... routingKeys) {
    Objects.requireNonNull(exchanges, "exchanges");
    if (queue == null || queue.isBlank()) {
      throw new IllegalArgumentException("queue is required");
    }
    if (routingKeys == null || routingKeys.length == 0) {
      throw new IllegalArgumentException("queue " + queue + " needs at least one routing key");
    }
    String deadLetters = deadLetterQueue(queue);
    Queue main =
        QueueBuilder.durable(queue)
            .deadLetterExchange(exchanges.deadLetter())
            .deadLetterRoutingKey(deadLetters)
            .quorum()
            .build();
    Queue dlq = QueueBuilder.durable(deadLetters).quorum().build();
    TopicExchange events = ExchangeBuilder.topicExchange(exchanges.events()).durable(true).build();
    DirectExchange dlx =
        ExchangeBuilder.directExchange(exchanges.deadLetter()).durable(true).build();
    List<Declarable> declarables =
        new ArrayList<>(
            List.of(events, dlx, main, dlq, BindingBuilder.bind(dlq).to(dlx).with(deadLetters)));
    for (String key : routingKeys) {
      if (key == null || key.isBlank()) {
        throw new IllegalArgumentException("queue " + queue + " has a blank routing key");
      }
      declarables.add(BindingBuilder.bind(main).to(events).with(key));
    }
    return new Declarables(declarables);
  }

  /**
   * The dead-letter queue of a consumer queue.
   *
   * @param queue the consumer queue
   * @return {@code <queue>.dlq}
   */
  public static String deadLetterQueue(String queue) {
    return queue + DLQ_SUFFIX;
  }
}
