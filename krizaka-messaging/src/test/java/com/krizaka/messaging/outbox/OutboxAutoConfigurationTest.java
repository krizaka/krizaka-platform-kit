package com.krizaka.messaging.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.AmqpTemplate;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.transaction.PlatformTransactionManager;

class OutboxAutoConfigurationTest {

  private final ApplicationContextRunner runner =
      new ApplicationContextRunner()
          .withConfiguration(AutoConfigurations.of(OutboxAutoConfiguration.class));

  @Test
  void withoutAStoreNothingIsScheduled() {
    runner.run(
        context -> {
          assertThat(context).hasSingleBean(OutboxRelayScheduler.class);
          assertThat(context.getBean(OutboxRelayScheduler.class).isRunning()).isFalse();
        });
  }

  @Test
  void aDeclaredStoreIsRelayed() {
    runner
        .withBean(OutboxStore.class, InMemoryOutboxStore::new)
        .withBean(AmqpTemplate.class, () -> mock(AmqpTemplate.class))
        .withBean(PlatformTransactionManager.class, () -> mock(PlatformTransactionManager.class))
        .run(
            context ->
                assertThat(context.getBean(OutboxRelayScheduler.class).isRunning()).isTrue());
  }

  @Test
  void aStoreWithoutABrokerFailsAtStartup() {
    runner
        .withBean(OutboxStore.class, InMemoryOutboxStore::new)
        .withBean(PlatformTransactionManager.class, () -> mock(PlatformTransactionManager.class))
        .run(
            context ->
                assertThat(context)
                    .hasFailed()
                    .getFailure()
                    .rootCause()
                    .hasMessageContaining("AmqpTemplate"));
  }

  @Test
  void canBeTurnedOff() {
    runner
        .withPropertyValues("krizaka.messaging.outbox.enabled=false")
        .withBean(OutboxStore.class, InMemoryOutboxStore::new)
        .run(context -> assertThat(context).doesNotHaveBean(OutboxRelayScheduler.class));
  }

  @Test
  void anApplicationWithoutRabbitMqStartsWithoutTheRelay() {
    runner
        .withClassLoader(new FilteredClassLoader(AmqpTemplate.class))
        .run(
            context ->
                assertThat(context).hasNotFailed().doesNotHaveBean(OutboxRelayScheduler.class));
  }
}
