package com.krizaka.messaging.consume;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.core.AmqpTemplate;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;

class DeadLetterQueueRecovererTest {

  private final AmqpTemplate template = mock(AmqpTemplate.class);
  private final DeadLetterQueueRecoverer recoverer =
      new DeadLetterQueueRecoverer(template, "platform.dlx");

  @Test
  void republishesToTheConsumerQueuesDlqKeepingTheEnvelope() {
    MessageProperties properties = new MessageProperties();
    properties.setConsumerQueue("krizaka.notifications.user-events");
    properties.setReceivedRoutingKey("evt.user.registered");
    properties.setHeader("kz-type", "evt.user.registered");
    properties.setMessageId("m-1");

    recoverer.recover(new Message("{}".getBytes(), properties), new IllegalStateException("boom"));

    ArgumentCaptor<Message> sent = ArgumentCaptor.forClass(Message.class);
    verify(template)
        .send(eq("platform.dlx"), eq("krizaka.notifications.user-events.dlq"), sent.capture());
    MessageProperties dead = sent.getValue().getMessageProperties();
    assertThat(dead.getHeaders())
        .containsEntry("kz-type", "evt.user.registered")
        .containsEntry("x-exception-message", "boom")
        .containsKey("x-exception-stacktrace");
    assertThat(dead.getMessageId()).isEqualTo("m-1");
  }

  @Test
  void withoutAConsumerQueueTheRoutingKeyNamesTheDlq() {
    MessageProperties properties = new MessageProperties();
    properties.setReceivedRoutingKey("evt.user.registered");

    recoverer.recover(new Message("{}".getBytes(), properties), new IllegalStateException("x"));

    verify(template).send(eq("platform.dlx"), eq("evt.user.registered.dlq"), any(Message.class));
  }
}
