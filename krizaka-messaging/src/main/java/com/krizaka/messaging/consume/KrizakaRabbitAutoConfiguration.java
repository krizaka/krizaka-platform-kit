package com.krizaka.messaging.consume;

import com.krizaka.messaging.topology.MessagingExchanges;
import org.springframework.amqp.core.AmqpTemplate;
import org.springframework.amqp.rabbit.config.RetryInterceptorBuilder;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.amqp.autoconfigure.SimpleRabbitListenerContainerFactoryConfigurer;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.json.JsonMapper;

/**
 * The consumer side of RabbitMQ, written once: JSON with the application's Jackson 3 mapper, and a
 * listener container that retries a failing listener with back-off, then dead-letters the message
 * to {@code <queue>.dlq}.
 *
 * <ul>
 *   <li><b>{@code MessageConverter}</b> — a {@link JacksonJsonMessageConverter} over the
 *       application's {@link JsonMapper} (the one its HTTP API uses), converting to the listener
 *       parameter's type, never to a class named in a header: producer and consumer share a JSON
 *       contract, not a Java package.
 *   <li><b>{@code rabbitListenerContainerFactory}</b> — Spring Boot's settings ({@code
 *       spring.rabbitmq.listener.simple.*}: concurrency, prefetch, acknowledgement, observation),
 *       then a stateless retry from {@link ConsumerRetryProperties}, then {@link
 *       DeadLetterQueueRecoverer}; a rejected message is never requeued.
 * </ul>
 *
 * <p>Each bean backs off when the application declares its own. The container factory also backs
 * off when the application already declared a retry policy through Spring Boot ({@code
 * spring.rabbitmq.listener.simple.retry.enabled=true}): two declared policies for one listener
 * would leave one of them silently unused, and that application's queues may dead-letter under
 * another routing key. {@code krizaka.messaging.consumer.enabled=false} turns everything off.
 */
@AutoConfiguration(
    beforeName = "org.springframework.boot.amqp.autoconfigure.RabbitAutoConfiguration",
    afterName = "org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration")
@ConditionalOnClass(RabbitTemplate.class)
@ConditionalOnProperty(
    prefix = "krizaka.messaging.consumer",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = true)
@EnableConfigurationProperties(ConsumerRetryProperties.class)
public class KrizakaRabbitAutoConfiguration {

  /** The bean name Spring AMQP's {@code @RabbitListener} uses by default. */
  public static final String CONTAINER_FACTORY = "rabbitListenerContainerFactory";

  /** Jackson 3 JSON, in its own configuration: Jackson is optional. */
  @Configuration(proxyBeanMethods = false)
  @ConditionalOnClass(JsonMapper.class)
  static class Json {

    /**
     * JSON with the application's mapper.
     *
     * @param json the application's mapper; Jackson's shared default when there is none
     * @return the converter used by Spring Boot's {@code RabbitTemplate} and by the listeners
     */
    @Bean
    @ConditionalOnMissingBean(MessageConverter.class)
    MessageConverter krizakaJsonMessageConverter(ObjectProvider<JsonMapper> json) {
      JacksonJsonMessageConverter converter =
          new JacksonJsonMessageConverter(json.getIfAvailable(JsonMapper::shared));
      converter.setAlwaysConvertToInferredType(true);
      return converter;
    }
  }

  /** The container factory, in its own configuration: Spring Boot's AMQP support is optional. */
  @Configuration(proxyBeanMethods = false)
  @ConditionalOnClass(SimpleRabbitListenerContainerFactoryConfigurer.class)
  @ConditionalOnMissingBean(name = CONTAINER_FACTORY)
  @ConditionalOnProperty(
      prefix = "spring.rabbitmq.listener.simple.retry",
      name = "enabled",
      havingValue = "false",
      matchIfMissing = true)
  static class Container {

    /**
     * Retry with back-off, then {@code <queue>.dlq}.
     *
     * @param configurer Spring Boot's settings, when its RabbitMQ auto-configuration runs
     * @param connectionFactory the broker
     * @param converter the message converter, when there is one
     * @param template the template dead letters are sent with
     * @param exchanges the dead-letter exchange
     * @param retry the retry policy
     * @return the default listener container factory
     */
    @Bean(name = CONTAINER_FACTORY)
    SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
        ObjectProvider<SimpleRabbitListenerContainerFactoryConfigurer> configurer,
        ObjectProvider<ConnectionFactory> connectionFactory,
        ObjectProvider<MessageConverter> converter,
        ObjectProvider<AmqpTemplate> template,
        ObjectProvider<MessagingExchanges> exchanges,
        ConsumerRetryProperties retry) {
      ConnectionFactory broker =
          required(connectionFactory.getIfUnique(), "a single RabbitMQ ConnectionFactory");
      SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
      SimpleRabbitListenerContainerFactoryConfigurer boot = configurer.getIfAvailable();
      if (boot != null) {
        boot.configure(factory, broker);
      } else {
        factory.setConnectionFactory(broker);
        converter.ifUnique(factory::setMessageConverter);
      }
      factory.setDefaultRequeueRejected(false);
      factory.setAdviceChain(
          RetryInterceptorBuilder.stateless()
              .maxRetries(retry.maxAttempts() - 1)
              .backOffOptions(
                  retry.initial().toMillis(), retry.multiplier(), retry.max().toMillis())
              .recoverer(
                  new DeadLetterQueueRecoverer(
                      required(template.getIfUnique(), "a single AmqpTemplate"),
                      exchanges.getIfAvailable(MessagingExchanges::defaults).deadLetter()))
              .build());
      return factory;
    }

    private static <T> T required(T bean, String what) {
      if (bean == null) {
        throw new IllegalStateException(
            "The krizaka listener container factory needs "
                + what
                + " (spring-boot-starter-amqp); set krizaka.messaging.consumer.enabled=false to"
                + " configure listeners yourself");
      }
      return bean;
    }
  }
}
