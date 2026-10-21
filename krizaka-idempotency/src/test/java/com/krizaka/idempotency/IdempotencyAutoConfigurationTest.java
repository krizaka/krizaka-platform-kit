package com.krizaka.idempotency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

class IdempotencyAutoConfigurationTest {

  private final ApplicationContextRunner runner =
      new ApplicationContextRunner()
          .withConfiguration(AutoConfigurations.of(IdempotencyAutoConfiguration.class));

  @Test
  void nothingUntilAStoreIsDeclared() {
    runner.run(context -> assertThat(context).doesNotHaveBean(Idempotency.class));
  }

  @Test
  void memoryStore() {
    runner
        .withPropertyValues("krizaka.idempotency.store=memory", "krizaka.idempotency.ttl=2h")
        .run(
            context -> {
              assertThat(context).hasSingleBean(Idempotency.class);
              assertThat(context.getBean(IdempotencyStore.class))
                  .isInstanceOf(InMemoryIdempotencyStore.class);
              assertThat(context.getBean(IdempotencyProperties.class).ttl())
                  .isEqualTo(Duration.ofHours(2));
              assertThat(context.getBean(IdempotencyPurgeScheduler.class).isRunning()).isTrue();
            });
  }

  @Test
  void jdbcStoreInTheApplicationsTransaction() {
    runner
        .withPropertyValues("krizaka.idempotency.store=jdbc")
        .withBean(JdbcTemplate.class, () -> mock(JdbcTemplate.class))
        .withBean(PlatformTransactionManager.class, () -> mock(PlatformTransactionManager.class))
        .run(
            context -> {
              assertThat(context.getBean(IdempotencyStore.class))
                  .isInstanceOf(JdbcIdempotencyStore.class);
              assertThat(context).hasSingleBean(Idempotency.class);
            });
  }

  @Test
  void jdbcWithoutADatabaseFailsInsteadOfFallingBackToMemory() {
    runner
        .withPropertyValues("krizaka.idempotency.store=jdbc")
        .run(
            context ->
                assertThat(context)
                    .hasFailed()
                    .getFailure()
                    .rootCause()
                    .hasMessageContaining("needs a JdbcTemplate"));
  }

  @Test
  void jdbcWithoutATransactionManagerFails() {
    runner
        .withPropertyValues("krizaka.idempotency.store=jdbc")
        .withBean(JdbcTemplate.class, () -> mock(JdbcTemplate.class))
        .run(
            context ->
                assertThat(context)
                    .hasFailed()
                    .getFailure()
                    .rootCause()
                    .hasMessageContaining("PlatformTransactionManager"));
  }

  @Test
  void jdbcWithoutSpringJdbcOnTheClasspathFails() {
    runner
        .withPropertyValues("krizaka.idempotency.store=jdbc")
        .withClassLoader(new FilteredClassLoader("org.springframework.jdbc"))
        .run(
            context ->
                assertThat(context)
                    .hasFailed()
                    .getFailure()
                    .rootCause()
                    .hasMessageContaining("spring-jdbc"));
  }

  @Test
  void anApplicationsOwnServiceWins() {
    Idempotency own = mock(Idempotency.class);
    runner
        .withPropertyValues("krizaka.idempotency.store=memory")
        .withBean(Idempotency.class, () -> own)
        .run(context -> assertThat(context.getBean(Idempotency.class)).isSameAs(own));
  }
}
