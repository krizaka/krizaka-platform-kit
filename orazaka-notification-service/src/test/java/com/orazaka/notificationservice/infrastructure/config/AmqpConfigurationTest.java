package com.orazaka.notificationservice.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.Queue;

class AmqpConfigurationTest {

  private final AmqpConfiguration configuration = new AmqpConfiguration();

  @Test
  void declaresEveryQueueWithItsDeadLetterQueue() {
    var declarables =
        configuration.notificationQueues(
            configuration.eventsExchange(), configuration.deadLetterExchange());

    var queues = declarables.getDeclarablesByType(Queue.class);
    assertThat(queues)
        .extracting(Queue::getName)
        .containsExactlyInAnyOrder(
            AmqpConstants.USER_NOTIFICATIONS_QUEUE,
            AmqpConstants.USER_NOTIFICATIONS_QUEUE + ".dlq",
            AmqpConstants.PASSWORD_NOTIFICATIONS_QUEUE,
            AmqpConstants.PASSWORD_NOTIFICATIONS_QUEUE + ".dlq",
            AmqpConstants.REQUESTS_QUEUE,
            AmqpConstants.REQUESTS_QUEUE + ".dlq");
    assertThat(queues)
        .filteredOn(queue -> !queue.getName().endsWith(".dlq"))
        .allSatisfy(
            queue ->
                assertThat(queue.getArguments())
                    .containsEntry("x-dead-letter-exchange", AmqpConstants.DLX_EXCHANGE)
                    .containsEntry("x-dead-letter-routing-key", queue.getName()));
    assertThat(declarables.getDeclarablesByType(Binding.class))
        .extracting(Binding::getRoutingKey)
        .contains("evt.user.*", "evt.password.*", "evt.notification.requested");
  }

  @Test
  void bindsToThePlatformExchanges() {
    assertThat(configuration.eventsExchange().getName()).isEqualTo("orazaka.events");
    assertThat(configuration.deadLetterExchange().getName()).isEqualTo("orazaka.dlx");
  }
}
