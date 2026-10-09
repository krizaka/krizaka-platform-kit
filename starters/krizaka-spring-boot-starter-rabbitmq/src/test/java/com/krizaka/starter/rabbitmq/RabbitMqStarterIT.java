package com.krizaka.starter.rabbitmq;

import static org.assertj.core.api.Assertions.assertThat;

import com.krizaka.messaging.dedup.MessageDedup;
import com.krizaka.messaging.topology.KrizakaQueues;
import com.krizaka.messaging.topology.MessagingExchanges;
import com.krizaka.test.container.AbstractContainerIntegrationTest;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * An application with only {@code krizaka-spring-boot-starter-rabbitmq} (and a JDBC driver) on a
 * real RabbitMQ and PostgreSQL: a consumer queue declared in one line receives a typed event
 * through the kit's JSON converter and listener container.
 */
@SpringBootTest(
    classes = RabbitMqStarterIT.App.class,
    properties = {
      "krizaka.messaging.producer=starter-rabbitmq-it",
      "krizaka.messaging.exchanges.events=starter-rabbitmq-it.events",
      "krizaka.messaging.exchanges.dead-letter=starter-rabbitmq-it.dlx",
      "krizaka.messaging.dedup.store=memory"
    })
class RabbitMqStarterIT extends AbstractContainerIntegrationTest {

  static final String QUEUE = "starter-rabbitmq-it.orders";

  @Autowired RabbitTemplate rabbit;
  @Autowired Listener listener;
  @Autowired JdbcTemplate jdbc;
  @Autowired MessageDedup dedup;

  /** The event, as the consumer's own copy. */
  record OrderPlaced(String orderId) {}

  @Test
  void aConsumerQueueReceivesATypedEvent() throws Exception {
    rabbit.convertAndSend("starter-rabbitmq-it.events", "evt.order.placed", new OrderPlaced("o-1"));

    assertThat(listener.received.poll(20, TimeUnit.SECONDS)).isEqualTo(new OrderPlaced("o-1"));
  }

  @Test
  void theDatabaseAndTheDeduplicationAreWired() {
    assertThat(jdbc.queryForObject("SELECT 1", Integer.class)).isOne();
    assertThat(dedup.claim(QUEUE, "m-1")).isTrue();
    assertThat(dedup.claim(QUEUE, "m-1")).isFalse();
  }

  /** The listener under test. */
  static class Listener {
    final BlockingQueue<OrderPlaced> received = new LinkedBlockingQueue<>();

    @RabbitListener(queues = QUEUE)
    void onOrderPlaced(OrderPlaced event) {
      received.add(event);
    }
  }

  /** The application: auto-configuration, one consumer queue and its listener. */
  @SpringBootConfiguration
  @EnableAutoConfiguration
  static class App {

    @Bean
    Declarables orders(MessagingExchanges exchanges) {
      return KrizakaQueues.consumer(exchanges, QUEUE, "evt.order.*");
    }

    @Bean
    Listener listener() {
      return new Listener();
    }
  }
}
