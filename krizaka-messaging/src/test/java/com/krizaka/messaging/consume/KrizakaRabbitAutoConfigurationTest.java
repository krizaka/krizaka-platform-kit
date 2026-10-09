package com.krizaka.messaging.consume;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.krizaka.messaging.topology.MessagingExchangesAutoConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.AmqpTemplate;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.config.StatelessRetryOperationsInterceptor;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.amqp.support.converter.SimpleMessageConverter;
import org.springframework.boot.amqp.autoconfigure.RabbitAutoConfiguration;
import org.springframework.boot.amqp.autoconfigure.SimpleRabbitListenerContainerFactoryConfigurer;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.json.JsonMapper;

class KrizakaRabbitAutoConfigurationTest {

  private final ApplicationContextRunner runner =
      new ApplicationContextRunner()
          .withConfiguration(
              AutoConfigurations.of(
                  MessagingExchangesAutoConfiguration.class, KrizakaRabbitAutoConfiguration.class))
          .withBean(ConnectionFactory.class, () -> mock(ConnectionFactory.class))
          .withBean(AmqpTemplate.class, () -> mock(AmqpTemplate.class));

  private final ApplicationContextRunner withSpringBoot =
      new ApplicationContextRunner()
          .withConfiguration(
              AutoConfigurations.of(
                  MessagingExchangesAutoConfiguration.class,
                  KrizakaRabbitAutoConfiguration.class,
                  RabbitAutoConfiguration.class));

  @Test
  void contributesJsonOverTheApplicationsMapperConvertingToTheListenersType() {
    JsonMapper mapper = JsonMapper.builder().build();
    runner
        .withBean(JsonMapper.class, () -> mapper)
        .run(
            context -> {
              MessageConverter converter = context.getBean(MessageConverter.class);
              assertThat(converter).isInstanceOf(JacksonJsonMessageConverter.class);
              assertThat(ReflectionTestUtils.getField(converter, "alwaysConvertToInferredType"))
                  .isEqualTo(true);
              assertThat(ReflectionTestUtils.getField(converter, "objectMapper")).isSameAs(mapper);
            });
  }

  @Test
  void contributesAFactoryThatRetriesThenDeadLettersAndNeverRequeues() {
    runner.run(
        context -> {
          SimpleRabbitListenerContainerFactory factory =
              context.getBean(
                  KrizakaRabbitAutoConfiguration.CONTAINER_FACTORY,
                  SimpleRabbitListenerContainerFactory.class);
          assertThat(factory.getAdviceChain())
              .singleElement()
              .isInstanceOf(StatelessRetryOperationsInterceptor.class);
          assertThat(ReflectionTestUtils.getField(factory, "defaultRequeueRejected"))
              .isEqualTo(false);
        });
  }

  @Test
  void runsBeforeSpringBootsRabbitConfigurationAndReplacesItsFactory() {
    withSpringBoot.run(
        context -> {
          assertThat(context).hasNotFailed();
          assertThat(context).hasSingleBean(SimpleRabbitListenerContainerFactory.class);
          assertThat(definedBy(context)).contains("KrizakaRabbitAutoConfiguration");
          assertThat(context.getBean(RabbitTemplate.class).getMessageConverter())
              .isInstanceOf(JacksonJsonMessageConverter.class);
        });
  }

  @Test
  void keepsSpringBootsListenerSettings() {
    withSpringBoot
        .withPropertyValues("spring.rabbitmq.listener.simple.prefetch=7")
        .run(
            context ->
                assertThat(
                        ReflectionTestUtils.getField(
                            context.getBean(SimpleRabbitListenerContainerFactory.class),
                            "prefetchCount"))
                    .isEqualTo(7));
  }

  @Test
  void anApplicationThatDeclaredSpringBootsRetryKeepsSpringBootsFactory() {
    withSpringBoot
        .withPropertyValues("spring.rabbitmq.listener.simple.retry.enabled=true")
        .run(
            context -> {
              assertThat(context).hasSingleBean(SimpleRabbitListenerContainerFactory.class);
              // Spring Boot's own factory, retry and recoverer: not the kit's dead-letter routing.
              assertThat(definedBy(context)).doesNotContain("Krizaka");
            });
  }

  @Test
  void theApplicationsOwnBeansWin() {
    SimpleMessageConverter converter = new SimpleMessageConverter();
    SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
    runner
        .withBean(MessageConverter.class, () -> converter)
        .withBean(
            KrizakaRabbitAutoConfiguration.CONTAINER_FACTORY,
            SimpleRabbitListenerContainerFactory.class,
            () -> factory)
        .run(
            context -> {
              assertThat(context.getBean(MessageConverter.class)).isSameAs(converter);
              assertThat(context.getBean(SimpleRabbitListenerContainerFactory.class))
                  .isSameAs(factory);
            });
  }

  @Test
  void canBeTurnedOff() {
    runner
        .withPropertyValues("krizaka.messaging.consumer.enabled=false")
        .run(
            context ->
                assertThat(context)
                    .doesNotHaveBean(MessageConverter.class)
                    .doesNotHaveBean(SimpleRabbitListenerContainerFactory.class));
  }

  @Test
  void aFactoryWithoutABrokerFailsAtStartupWithTheReason() {
    new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(KrizakaRabbitAutoConfiguration.class))
        .run(
            context ->
                assertThat(context)
                    .hasFailed()
                    .getFailure()
                    .rootCause()
                    .hasMessageContaining("ConnectionFactory")
                    .hasMessageContaining("krizaka.messaging.consumer.enabled=false"));
  }

  @Test
  void anApplicationWithoutRabbitMqStartsWithoutAnyOfIt() {
    new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(KrizakaRabbitAutoConfiguration.class))
        .withClassLoader(new FilteredClassLoader(RabbitTemplate.class))
        .run(
            context ->
                assertThat(context)
                    .hasNotFailed()
                    .doesNotHaveBean(ConsumerRetryProperties.class)
                    .doesNotHaveBean(SimpleRabbitListenerContainerFactory.class));
  }

  @Test
  void anApplicationWithoutJacksonKeepsTheFactoryWithoutTheConverter() {
    runner
        .withClassLoader(new FilteredClassLoader(JsonMapper.class))
        .run(
            context ->
                assertThat(context)
                    .hasNotFailed()
                    .doesNotHaveBean(MessageConverter.class)
                    .hasSingleBean(SimpleRabbitListenerContainerFactory.class));
  }

  @Test
  void anApplicationWithoutSpringBootsAmqpSupportKeepsTheConverterOnly() {
    runner
        .withClassLoader(
            new FilteredClassLoader(SimpleRabbitListenerContainerFactoryConfigurer.class))
        .run(
            context ->
                assertThat(context)
                    .hasNotFailed()
                    .hasSingleBean(MessageConverter.class)
                    .doesNotHaveBean(SimpleRabbitListenerContainerFactory.class));
  }

  private static String definedBy(
      org.springframework.context.ConfigurableApplicationContext context) {
    return context
        .getBeanFactory()
        .getBeanDefinition(KrizakaRabbitAutoConfiguration.CONTAINER_FACTORY)
        .getFactoryBeanName();
  }
}
