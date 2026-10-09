package com.krizaka.messaging.topology;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;

class KrizakaQueuesTest {

  private final MessagingExchanges exchanges =
      new MessagingExchanges("platform.events", "platform.dlx");

  @Test
  void theQueueIsAQuorumQueueThatDeadLettersToItsDlq() {
    Declarables declarables =
        KrizakaQueues.consumer(
            exchanges, "krizaka.notifications.user-events", "evt.user.registered");

    Queue main = queue(declarables, "krizaka.notifications.user-events");
    assertThat(main.isDurable()).isTrue();
    assertThat(main.getArguments())
        .containsEntry("x-queue-type", "quorum")
        .containsEntry("x-dead-letter-exchange", "platform.dlx")
        .containsEntry("x-dead-letter-routing-key", "krizaka.notifications.user-events.dlq");
    Queue dlq = queue(declarables, "krizaka.notifications.user-events.dlq");
    assertThat(dlq.isDurable()).isTrue();
    assertThat(dlq.getArguments()).containsEntry("x-queue-type", "quorum");
  }

  @Test
  void declaresBothExchangesAndBindsEachRoutingKey() {
    Declarables declarables =
        KrizakaQueues.consumer(
            exchanges,
            "krizaka.notifications.user-events",
            "evt.user.registered",
            "evt.user.verified");

    assertThat(declarables.getDeclarablesByType(TopicExchange.class))
        .singleElement()
        .satisfies(e -> assertThat(e.getName()).isEqualTo("platform.events"));
    assertThat(declarables.getDeclarablesByType(DirectExchange.class))
        .singleElement()
        .satisfies(e -> assertThat(e.getName()).isEqualTo("platform.dlx"));
    List<Binding> bindings = declarables.getDeclarablesByType(Binding.class);
    assertThat(bindings)
        .extracting(Binding::getExchange, Binding::getDestination, Binding::getRoutingKey)
        .containsExactlyInAnyOrder(
            org.assertj.core.groups.Tuple.tuple(
                "platform.dlx",
                "krizaka.notifications.user-events.dlq",
                "krizaka.notifications.user-events.dlq"),
            org.assertj.core.groups.Tuple.tuple(
                "platform.events", "krizaka.notifications.user-events", "evt.user.registered"),
            org.assertj.core.groups.Tuple.tuple(
                "platform.events", "krizaka.notifications.user-events", "evt.user.verified"));
  }

  @Test
  void aQueueWithoutARoutingKeyReceivesNothingAndIsRefused() {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> KrizakaQueues.consumer(exchanges, "krizaka.x.y"));
    assertThatIllegalArgumentException()
        .isThrownBy(() -> KrizakaQueues.consumer(exchanges, "krizaka.x.y", " "));
    assertThatIllegalArgumentException()
        .isThrownBy(() -> KrizakaQueues.consumer(exchanges, "", "evt.a.b"));
  }

  private static Queue queue(Declarables declarables, String name) {
    return declarables.getDeclarablesByType(Queue.class).stream()
        .filter(q -> q.getName().equals(name))
        .findFirst()
        .orElseThrow();
  }
}
