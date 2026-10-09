package com.krizaka.messaging.dedup;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;

class MessageDedupAutoConfigurationTest {

  private final ApplicationContextRunner runner =
      new ApplicationContextRunner()
          .withConfiguration(AutoConfigurations.of(MessageDedupAutoConfiguration.class));

  @Test
  void contributesNothingUntilAStoreIsDeclared() {
    runner.run(context -> assertThat(context).doesNotHaveBean(MessageDedup.class));
  }

  @Test
  void memoryStore() {
    runner
        .withPropertyValues("krizaka.messaging.dedup.store=memory")
        .run(
            context ->
                assertThat(context.getBean(MessageDedup.class))
                    .isInstanceOf(InMemoryMessageDedup.class));
  }

  @Test
  void jdbcStore() {
    runner
        .withPropertyValues("krizaka.messaging.dedup.store=jdbc")
        .withBean(
            JdbcTemplate.class,
            () ->
                new JdbcTemplate(
                    new EmbeddedDatabaseBuilder()
                        .setType(EmbeddedDatabaseType.H2)
                        .generateUniqueName(true)
                        .build()))
        .run(
            context ->
                assertThat(context.getBean(MessageDedup.class))
                    .isInstanceOf(JdbcMessageDedup.class));
  }

  @Test
  void aJdbcStoreWithoutADatabaseFailsInsteadOfFallingBackToMemory() {
    runner
        .withPropertyValues("krizaka.messaging.dedup.store=jdbc")
        .run(
            context ->
                assertThat(context)
                    .hasFailed()
                    .getFailure()
                    .rootCause()
                    .hasMessageContaining("needs a JdbcTemplate"));
  }

  @Test
  void aMemoryStoreStartsWithoutJdbcOnTheClasspath() {
    runner
        .withClassLoader(new FilteredClassLoader(JdbcTemplate.class))
        .withPropertyValues("krizaka.messaging.dedup.store=memory")
        .run(
            context ->
                assertThat(context.getBean(MessageDedup.class))
                    .isInstanceOf(InMemoryMessageDedup.class));
  }

  @Test
  void aJdbcStoreWithoutJdbcOnTheClasspathSaysSo() {
    runner
        .withClassLoader(new FilteredClassLoader(JdbcTemplate.class))
        .withPropertyValues("krizaka.messaging.dedup.store=jdbc")
        .run(
            context ->
                assertThat(context)
                    .hasFailed()
                    .getFailure()
                    .rootCause()
                    .hasMessageContaining("needs spring-jdbc"));
  }
}
