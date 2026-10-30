package com.krizaka.notifications.service.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.krizaka.messaging.topology.MessagingExchanges;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.Queue;

class AmqpConfigurationTest {

  private final AmqpConfiguration configuration = new AmqpConfiguration();
  private final MessagingExchanges platform =
      new MessagingExchanges("platform.events", "platform.dlx");

  @Test
  void declaresEveryQueueWithItsDeadLetterQueue() {
    var declarables =
        configuration.notificationQueues(
            configuration.eventsExchange(platform), configuration.deadLetterExchange(platform));

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
                    .containsEntry("x-dead-letter-exchange", "platform.dlx")
                    .containsEntry("x-dead-letter-routing-key", queue.getName()));
    assertThat(declarables.getDeclarablesByType(Binding.class))
        .extracting(Binding::getRoutingKey)
        .contains("evt.user.*", "evt.password.*", "evt.notification.requested");
  }

  @Test
  void bindsToTheExchangesThePlatformNames() {
    assertThat(configuration.eventsExchange(platform).getName()).isEqualTo("platform.events");
    assertThat(configuration.deadLetterExchange(platform).getName()).isEqualTo("platform.dlx");
  }

  @Test
  void standaloneItRunsOnTheKrizakaExchanges() {
    MessagingExchanges defaults = MessagingExchanges.defaults();
    assertThat(configuration.eventsExchange(defaults).getName()).isEqualTo("krizaka.events");
    assertThat(configuration.deadLetterExchange(defaults).getName()).isEqualTo("krizaka.dlx");
  }
}
