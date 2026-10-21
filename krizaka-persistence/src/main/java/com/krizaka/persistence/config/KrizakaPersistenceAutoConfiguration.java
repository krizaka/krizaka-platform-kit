package com.krizaka.persistence.config;

import com.krizaka.persistence.KrizakaMigrations;
import javax.sql.DataSource;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;

/**
 * Migrates the tables of the declared JDBC stores at startup ({@link KrizakaMigrations}), when the
 * application has a {@code DataSource}. {@code krizaka.persistence.migrations.enabled=false} leaves
 * them to an operator, who runs the shipped schemas.
 */
@AutoConfiguration(
    afterName = "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration")
@ConditionalOnBean(DataSource.class)
@ConditionalOnProperty(
    prefix = "krizaka.persistence.migrations",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = true)
@EnableConfigurationProperties(KrizakaPersistenceProperties.class)
public class KrizakaPersistenceAutoConfiguration {

  /** Creates the auto-configuration. */
  public KrizakaPersistenceAutoConfiguration() {}

  /**
   * The kit's migrations, run while the context starts.
   *
   * @param dataSource the service's database
   * @param properties which stores are declared, and their table names
   * @param persistence the history table
   * @return the migrations, applied when the bean is initialised
   */
  @Bean
  @ConditionalOnMissingBean
  public KrizakaMigrations krizakaMigrations(
      DataSource dataSource, Environment properties, KrizakaPersistenceProperties persistence) {
    ClassLoader classLoader = KrizakaPersistenceAutoConfiguration.class.getClassLoader();
    return new KrizakaMigrations(
        dataSource,
        KitSchema.declared(properties, classLoader),
        persistence.migrations().historyTable(),
        classLoader);
  }
}
