package com.krizaka.messaging.event;

import static org.assertj.core.api.Assertions.assertThat;

import com.krizaka.messaging.outbox.OutboxStore;
import com.krizaka.messaging.topology.MessagingExchangesAutoConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.json.JsonMapper;

class EventPublisherAutoConfigurationTest {

  private final ApplicationContextRunner runner =
      new ApplicationContextRunner()
          .withConfiguration(
              AutoConfigurations.of(
                  MessagingExchangesAutoConfiguration.class,
                  EventPublisherAutoConfiguration.class));

  /** A service component that publishes events. */
  record Registration(EventPublisher events) {}

  @Configuration(proxyBeanMethods = false)
  static class Publishing {
    @Bean
    Registration registration(EventPublisher events) {
      return new Registration(events);
    }
  }

  @Test
  void aServiceThatNeverPublishesStartsWithoutDeclaringAProducer() {
    runner
        .withBean(OutboxStore.class, RecordingOutboxStore::new)
        .run(context -> assertThat(context).hasNotFailed());
  }

  @Test
  void injectingAPublisherWithoutAProducerFailsAtStartupWithTheReason() {
    runner
        .withUserConfiguration(Publishing.class)
        .withBean(OutboxStore.class, RecordingOutboxStore::new)
        .run(
            context ->
                assertThat(context)
                    .hasFailed()
                    .getFailure()
                    .rootCause()
                    .hasMessageContaining("krizaka.messaging.producer"));
  }

  @Test
  void injectingAPublisherWithoutAnOutboxFailsAtStartupWithTheReason() {
    runner
        .withUserConfiguration(Publishing.class)
        .withPropertyValues("krizaka.messaging.producer=krizaka-users")
        .run(
            context ->
                assertThat(context)
                    .hasFailed()
                    .getFailure()
                    .rootCause()
                    .hasMessageContaining("exactly one OutboxStore"));
  }

  @Test
  void aDeclaredProducerAndOneOutboxGiveTheOutboxPublisher() {
    runner
        .withUserConfiguration(Publishing.class)
        .withPropertyValues("krizaka.messaging.producer=krizaka-users")
        .withBean(OutboxStore.class, RecordingOutboxStore::new)
        .withBean(JsonMapper.class, JsonMapper::shared)
        .run(
            context ->
                assertThat(context.getBean(Registration.class).events())
                    .isInstanceOf(OutboxEventPublisher.class));
  }

  @Test
  void theApplicationsOwnPublisherWins() {
    EventPublisher own = (routingKey, version, event) -> {};
    runner
        .withBean(EventPublisher.class, () -> own)
        .run(context -> assertThat(context.getBean(EventPublisher.class)).isSameAs(own));
  }

  @Test
  void anApplicationWithoutJacksonStartsWithoutAPublisher() {
    runner
        .withClassLoader(new FilteredClassLoader(JsonMapper.class))
        .withPropertyValues("krizaka.messaging.producer=krizaka-users")
        .run(context -> assertThat(context).hasNotFailed().doesNotHaveBean(EventPublisher.class));
  }
}
