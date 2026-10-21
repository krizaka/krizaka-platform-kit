package com.krizaka.idempotency;

import io.micrometer.observation.ObservationRegistry;
import java.time.Clock;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

/**
 * Contributes {@link Idempotency} over the store the application declared with {@code
 * krizaka.idempotency.store}.
 *
 * <p>No declaration, no bean: a controller that needs idempotency then fails at startup, where a
 * missing guarantee should be found. {@code store=jdbc} without a {@code JdbcTemplate} or a
 * transaction manager fails too, rather than degrading to memory. Each bean backs off when the
 * application declares its own.
 */
@AutoConfiguration(
    afterName = {
      "org.springframework.boot.jdbc.autoconfigure.JdbcTemplateAutoConfiguration",
      "org.springframework.boot.jdbc.autoconfigure.DataSourceTransactionManagerAutoConfiguration",
      "org.springframework.boot.transaction.autoconfigure.TransactionAutoConfiguration",
      "org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration"
    })
@ConditionalOnProperty(prefix = "krizaka.idempotency", name = "store")
@EnableConfigurationProperties(IdempotencyProperties.class)
public class IdempotencyAutoConfiguration {

  private static final String JDBC_TEMPLATE = "org.springframework.jdbc.core.JdbcTemplate";

  /** Creates the auto-configuration. */
  public IdempotencyAutoConfiguration() {}

  /**
   * Keys in memory ({@code store=memory}).
   *
   * @return the store
   */
  @Bean
  @ConditionalOnMissingBean(IdempotencyStore.class)
  @ConditionalOnProperty(prefix = "krizaka.idempotency", name = "store", havingValue = "memory")
  public IdempotencyStore inMemoryIdempotencyStore() {
    return new InMemoryIdempotencyStore();
  }

  /**
   * The service.
   *
   * @param store the store
   * @param json the application's mapper; Jackson's shared default when there is none
   * @param transactionManager the application's, required by the JDBC store
   * @param observations where executions are observed, when Micrometer is configured
   * @param properties the time-to-live
   * @return the service
   */
  @Bean
  @ConditionalOnMissingBean(Idempotency.class)
  public Idempotency idempotency(
      IdempotencyStore store,
      ObjectProvider<JsonMapper> json,
      ObjectProvider<PlatformTransactionManager> transactionManager,
      ObjectProvider<ObservationRegistry> observations,
      IdempotencyProperties properties) {
    TransactionTemplate transactions = null;
    if (store.transactional()) {
      PlatformTransactionManager manager = transactionManager.getIfUnique();
      if (manager == null) {
        throw new IllegalStateException(
            "krizaka.idempotency.store=jdbc needs a single PlatformTransactionManager: the claim,"
                + " the action and its response share one transaction");
      }
      transactions = new TransactionTemplate(manager);
    }
    return new Idempotency(
        store,
        json.getIfAvailable(JsonMapper::shared),
        transactions,
        properties.ttl(),
        Clock.systemUTC(),
        observations.getIfAvailable(() -> ObservationRegistry.NOOP));
  }

  /**
   * Forgets expired keys every {@code purge-interval}.
   *
   * @param store the store
   * @param properties the time-to-live and the interval
   * @return the scheduler, started with the context
   */
  @Bean
  @ConditionalOnMissingBean(IdempotencyPurgeScheduler.class)
  public IdempotencyPurgeScheduler idempotencyPurgeScheduler(
      IdempotencyStore store, IdempotencyProperties properties) {
    return new IdempotencyPurgeScheduler(
        store, properties.ttl(), properties.purgeInterval(), Clock.systemUTC());
  }

  /** Keys in the application's database ({@code store=jdbc}). */
  @Configuration(proxyBeanMethods = false)
  @ConditionalOnClass(name = JDBC_TEMPLATE)
  @ConditionalOnProperty(prefix = "krizaka.idempotency", name = "store", havingValue = "jdbc")
  static class Jdbc {
    @Bean
    @ConditionalOnMissingBean(IdempotencyStore.class)
    IdempotencyStore jdbcIdempotencyStore(
        IdempotencyProperties properties, ObjectProvider<JdbcTemplate> jdbcTemplate) {
      JdbcTemplate jdbc = jdbcTemplate.getIfAvailable();
      if (jdbc == null) {
        throw new IllegalStateException(
            "krizaka.idempotency.store=jdbc needs a JdbcTemplate (spring-boot-starter-jdbc and a"
                + " DataSource)");
      }
      return new JdbcIdempotencyStore(jdbc, properties.table());
    }
  }

  /** {@code store=jdbc} with no JDBC on the classpath: refuse to start, with the reason. */
  @Configuration(proxyBeanMethods = false)
  @ConditionalOnMissingClass(JDBC_TEMPLATE)
  @ConditionalOnProperty(prefix = "krizaka.idempotency", name = "store", havingValue = "jdbc")
  static class JdbcMissing {
    JdbcMissing() {
      throw new IllegalStateException(
          "krizaka.idempotency.store=jdbc needs spring-jdbc on the classpath"
              + " (spring-boot-starter-jdbc)");
    }
  }
}
