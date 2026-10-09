package com.krizaka.messaging.event;

import com.krizaka.messaging.outbox.OutboxStore;
import com.krizaka.messaging.topology.MessagingExchanges;
import java.time.Clock;
import java.util.List;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Lazy;
import tools.jackson.databind.json.JsonMapper;

/**
 * Contributes the {@link EventPublisher}, built the first time something injects it.
 *
 * <p>Lazy on purpose: a service that never publishes an event (a pure consumer, a service that
 * still appends its outbox rows by hand) declares nothing and starts. A service that injects one
 * must have declared who it is — {@code krizaka.messaging.producer} — and own exactly one {@link
 * OutboxStore}; otherwise it fails at startup, naming what is missing. The producer name is never
 * guessed from {@code spring.application.name}: it is stamped on every event, and a consumer that
 * trusts it must be able to trust that somebody wrote it.
 */
@AutoConfiguration(
    afterName = {
      "org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration",
      "com.krizaka.messaging.topology.MessagingExchangesAutoConfiguration"
    })
@ConditionalOnClass(JsonMapper.class)
@EnableConfigurationProperties(EventPublisherProperties.class)
public class EventPublisherAutoConfiguration {

  /**
   * The publisher over the application's outbox.
   *
   * @param properties the producer name
   * @param stores the application's outbox stores; exactly one is required
   * @param json the application's mapper; Jackson's shared default when there is none
   * @param exchanges the events exchange
   * @return the publisher
   */
  @Bean
  @Lazy
  @ConditionalOnMissingBean(EventPublisher.class)
  public EventPublisher eventPublisher(
      EventPublisherProperties properties,
      ObjectProvider<OutboxStore> stores,
      ObjectProvider<JsonMapper> json,
      ObjectProvider<MessagingExchanges> exchanges) {
    String producer = properties.producer();
    if (producer == null || producer.isBlank()) {
      throw new IllegalStateException(
          "An EventPublisher is injected but krizaka.messaging.producer is not set: declare the"
              + " name of this service, stamped on every event it publishes (kz-producer).");
    }
    List<OutboxStore> declared = stores.orderedStream().toList();
    if (declared.size() != 1) {
      throw new IllegalStateException(
          "An EventPublisher writes to exactly one OutboxStore; found "
              + declared.size()
              + ". Declare the outbox of this context as an OutboxStore bean, or build"
              + " OutboxEventPublisher by hand for each store.");
    }
    return new OutboxEventPublisher(
        declared.getFirst(),
        json.getIfAvailable(JsonMapper::shared),
        exchanges.getIfAvailable(MessagingExchanges::defaults),
        producer,
        Clock.systemUTC());
  }
}
