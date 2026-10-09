package com.krizaka.messaging.outbox;

import java.time.Clock;
import java.util.List;
import org.springframework.amqp.core.AmqpTemplate;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Schedules a relay for every {@link OutboxStore} bean the application declares.
 *
 * <p>Nothing happens without a store: the kit never creates a table or a store of its own. With
 * one, it needs RabbitMQ ({@link AmqpTemplate}) and one {@link PlatformTransactionManager}; set
 * {@code krizaka.messaging.outbox.enabled=false} to turn the relay off (tests, read replicas).
 */
@AutoConfiguration(
    afterName = {
      "org.springframework.boot.amqp.autoconfigure.RabbitAutoConfiguration",
      "org.springframework.boot.jdbc.autoconfigure.DataSourceTransactionManagerAutoConfiguration",
      "org.springframework.boot.transaction.autoconfigure.TransactionAutoConfiguration"
    })
@ConditionalOnClass(AmqpTemplate.class)
@ConditionalOnProperty(
    prefix = "krizaka.messaging.outbox",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = true)
@EnableConfigurationProperties(OutboxProperties.class)
public class OutboxAutoConfiguration {

  /**
   * One relay per store, on one scheduler thread.
   *
   * @param stores the application's outbox stores; none means nothing is scheduled
   * @param amqp where rows are published
   * @param transactionManager the transaction each batch runs in
   * @param properties intervals, batch size and retention
   * @return the scheduler, started with the application context
   */
  @Bean
  @ConditionalOnMissingBean(OutboxRelayScheduler.class)
  public OutboxRelayScheduler outboxRelayScheduler(
      ObjectProvider<OutboxStore> stores,
      ObjectProvider<AmqpTemplate> amqp,
      ObjectProvider<PlatformTransactionManager> transactionManager,
      OutboxProperties properties) {
    List<OutboxStore> declared = stores.orderedStream().toList();
    if (declared.isEmpty()) {
      return new OutboxRelayScheduler(
          List.of(), properties.pollInterval(), properties.purgeInterval());
    }
    AmqpTemplate template =
        required(amqp.getIfUnique(), "a single AmqpTemplate (spring-boot-starter-amqp)");
    TransactionTemplate transactions =
        new TransactionTemplate(
            required(transactionManager.getIfUnique(), "a single PlatformTransactionManager"));
    List<OutboxRelay> relays =
        declared.stream()
            .map(
                store ->
                    new OutboxRelay(
                        store,
                        template,
                        transactions,
                        properties.batchSize(),
                        properties.retention(),
                        Clock.systemUTC()))
            .toList();
    return new OutboxRelayScheduler(relays, properties.pollInterval(), properties.purgeInterval());
  }

  private static <T> T required(T bean, String what) {
    if (bean == null) {
      throw new IllegalStateException("An OutboxStore is declared but there is not " + what);
    }
    return bean;
  }
}
