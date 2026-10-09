package com.krizaka.messaging.dedup;

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

/**
 * Contributes the {@link MessageDedup} the application declared with {@code
 * krizaka.messaging.dedup.store}.
 *
 * <p>No declaration, no bean: a listener that needs deduplication then fails at startup, which is
 * where a missing guarantee should be found. {@code store=jdbc} without a {@link JdbcTemplate}
 * fails too, rather than degrading to memory.
 *
 * <p>The JDBC store lives in its own nested configuration: {@code spring-jdbc} is optional, and a
 * bean method whose signature names {@code JdbcTemplate} cannot even be introspected without it — a
 * stateless service declaring {@code store=memory} must start with no JDBC on its classpath.
 */
@AutoConfiguration(
    afterName = "org.springframework.boot.jdbc.autoconfigure.JdbcTemplateAutoConfiguration")
@ConditionalOnProperty(prefix = "krizaka.messaging.dedup", name = "store")
@EnableConfigurationProperties(MessageDedupProperties.class)
public class MessageDedupAutoConfiguration {

  private static final String JDBC_TEMPLATE = "org.springframework.jdbc.core.JdbcTemplate";

  /**
   * Claims held in memory ({@code store=memory}).
   *
   * @return the in-memory deduplication service
   */
  @Bean
  @ConditionalOnMissingBean(MessageDedup.class)
  @ConditionalOnProperty(prefix = "krizaka.messaging.dedup", name = "store", havingValue = "memory")
  public MessageDedup inMemoryMessageDedup() {
    return new InMemoryMessageDedup();
  }

  /** Claims stored in the application's database ({@code store=jdbc}). */
  @Configuration(proxyBeanMethods = false)
  @ConditionalOnClass(name = JDBC_TEMPLATE)
  @ConditionalOnProperty(prefix = "krizaka.messaging.dedup", name = "store", havingValue = "jdbc")
  static class Jdbc {

    /**
     * The durable claim store.
     *
     * @param properties the claims table
     * @param jdbcTemplate the database
     * @return the JDBC deduplication service
     */
    @Bean
    @ConditionalOnMissingBean(MessageDedup.class)
    MessageDedup jdbcMessageDedup(
        MessageDedupProperties properties, ObjectProvider<JdbcTemplate> jdbcTemplate) {
      JdbcTemplate jdbc = jdbcTemplate.getIfAvailable();
      if (jdbc == null) {
        throw new IllegalStateException(
            "krizaka.messaging.dedup.store=jdbc needs a JdbcTemplate (spring-boot-starter-jdbc"
                + " and a DataSource)");
      }
      return new JdbcMessageDedup(jdbc, properties.table());
    }
  }

  /** {@code store=jdbc} with no JDBC on the classpath: refuse to start, with the reason. */
  @Configuration(proxyBeanMethods = false)
  @ConditionalOnMissingClass(JDBC_TEMPLATE)
  @ConditionalOnProperty(prefix = "krizaka.messaging.dedup", name = "store", havingValue = "jdbc")
  static class JdbcMissing {

    JdbcMissing() {
      throw new IllegalStateException(
          "krizaka.messaging.dedup.store=jdbc needs spring-jdbc on the classpath"
              + " (spring-boot-starter-jdbc)");
    }
  }
}
