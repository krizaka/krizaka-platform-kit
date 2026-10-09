package com.krizaka.messaging.consume;

import com.krizaka.messaging.topology.KrizakaQueues;
import org.springframework.amqp.core.AmqpTemplate;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.retry.RepublishMessageRecoverer;

/**
 * Sends a message that exhausted its retries to its consumer queue's dead-letter queue.
 *
 * <p>It is republished to the dead-letter exchange with the routing key {@code <consumerQueue>.dlq}
 * — the binding {@link KrizakaQueues#consumer} declares — keeping every original header (the event
 * envelope included) and adding Spring AMQP's {@code x-exception-*}, {@code x-original-exchange}
 * and {@code x-original-routingKey}, so the dead letter says what failed and why.
 */
public class DeadLetterQueueRecoverer extends RepublishMessageRecoverer {

  /**
   * A recoverer republishing through {@code template} to {@code deadLetterExchange}.
   *
   * @param template the template the dead letter is sent with
   * @param deadLetterExchange the dead-letter exchange
   */
  public DeadLetterQueueRecoverer(AmqpTemplate template, String deadLetterExchange) {
    super(template, deadLetterExchange);
  }

  /**
   * The dead-letter routing key.
   *
   * @param message the failed message
   * @return {@code <consumerQueue>.dlq}; the original routing key with {@code .dlq} when the
   *     message carries no consumer queue
   */
  @Override
  protected String prefixedOriginalRoutingKey(Message message) {
    String queue = message.getMessageProperties().getConsumerQueue();
    return KrizakaQueues.deadLetterQueue(
        queue != null ? queue : message.getMessageProperties().getReceivedRoutingKey());
  }
}
