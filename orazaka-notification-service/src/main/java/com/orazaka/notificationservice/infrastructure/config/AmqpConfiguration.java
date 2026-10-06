package com.orazaka.notificationservice.infrastructure.config;

import java.util.List;
import java.util.Map;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Declarable;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ topology of the notification service (AGENTS.md §6): three queues bound to the shared
 * {@code orazaka.events} topic exchange, each dead-lettering to its {@code <queue>.dlq} through
 * {@code orazaka.dlx}. Exchange declarations are idempotent duplicates of the platform topology so
 * the service can start before any producer has declared them.
 */
@Configuration
public class AmqpConfiguration {

  @Bean
  public TopicExchange eventsExchange() {
    return new TopicExchange(AmqpConstants.EVENTS_EXCHANGE, true, false);
  }

  @Bean
  public DirectExchange deadLetterExchange() {
    return new DirectExchange(AmqpConstants.DLX_EXCHANGE, true, false);
  }

  @Bean
  public Declarables notificationQueues(
      TopicExchange eventsExchange, DirectExchange deadLetterExchange) {
    return new Declarables(
        List.of(
                queue(
                    AmqpConstants.USER_NOTIFICATIONS_QUEUE,
                    AmqpConstants.USER_NOTIFICATIONS_DLQ,
                    AmqpConstants.USER_NOTIFICATIONS_BINDING,
                    eventsExchange,
                    deadLetterExchange),
                queue(
                    AmqpConstants.PASSWORD_NOTIFICATIONS_QUEUE,
                    AmqpConstants.PASSWORD_NOTIFICATIONS_DLQ,
                    AmqpConstants.PASSWORD_NOTIFICATIONS_BINDING,
                    eventsExchange,
                    deadLetterExchange),
                queue(
                    AmqpConstants.REQUESTS_QUEUE,
                    AmqpConstants.REQUESTS_DLQ,
                    AmqpConstants.REQUESTS_BINDING,
                    eventsExchange,
                    deadLetterExchange))
            .stream()
            .flatMap(List::stream)
            .toList());
  }

  @Bean
  public MessageConverter jsonMessageConverter() {
    return new JacksonJsonMessageConverter();
  }

  /** A queue, its binding, its DLQ and the DLQ's binding — the four declarations of one lane. */
  private static List<Declarable> queue(
      String name,
      String deadLetterName,
      String routingKey,
      TopicExchange events,
      DirectExchange deadLetters) {
    Queue queue =
        new Queue(
            name,
            true,
            false,
            false,
            Map.of(
                "x-dead-letter-exchange",
                AmqpConstants.DLX_EXCHANGE,
                "x-dead-letter-routing-key",
                name));
    Queue dlq = new Queue(deadLetterName, true, false, false);
    Binding binding = BindingBuilder.bind(queue).to(events).with(routingKey);
    Binding dlqBinding = BindingBuilder.bind(dlq).to(deadLetters).with(name);
    return List.of(queue, binding, dlq, dlqBinding);
  }
}
