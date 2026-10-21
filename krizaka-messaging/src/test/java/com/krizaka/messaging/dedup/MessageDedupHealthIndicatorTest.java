package com.krizaka.messaging.dedup;

import static org.assertj.core.api.Assertions.assertThat;

import com.krizaka.messaging.RecordedObservations;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.Status;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;

class MessageDedupHealthIndicatorTest {

  private static JdbcTemplate h2() {
    return new JdbcTemplate(
        new EmbeddedDatabaseBuilder()
            .setType(EmbeddedDatabaseType.H2)
            .generateUniqueName(true)
            .build());
  }

  @Test
  void aJdbcStoreWhoseTableAnswersIsUp() {
    JdbcTemplate jdbc = h2();
    jdbc.execute(
        "CREATE TABLE processed_messages (consumer VARCHAR(255) NOT NULL, message_id VARCHAR(255) NOT NULL,"
            + " processed_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL, PRIMARY KEY (consumer, message_id))");

    Health health = new MessageDedupHealthIndicator(new JdbcMessageDedup(jdbc)).health();

    assertThat(health.getStatus()).isEqualTo(Status.UP);
    assertThat(health.getDetails()).containsEntry("store", "JdbcMessageDedup");
  }

  @Test
  void aJdbcStoreWithoutItsTableIsDown() {
    Health health = new MessageDedupHealthIndicator(new JdbcMessageDedup(h2())).health();

    assertThat(health.getStatus()).isEqualTo(Status.DOWN);
    assertThat(health.getDetails()).containsKey("error");
  }

  @Test
  void anObservedStoreIsCheckedAndNamedThroughItsDelegate() {
    MessageDedup observed =
        ObservedMessageDedup.of(new JdbcMessageDedup(h2()), new RecordedObservations().registry());

    Health health = new MessageDedupHealthIndicator(observed).health();

    assertThat(health.getStatus()).isEqualTo(Status.DOWN);
    assertThat(health.getDetails()).containsEntry("store", "JdbcMessageDedup");
  }

  @Test
  void aMemoryStoreIsUp() {
    assertThat(new MessageDedupHealthIndicator(new InMemoryMessageDedup()).health().getStatus())
        .isEqualTo(Status.UP);
  }

  @Test
  void contributedWithAStoreTurnedOffByPropertyAbsentWithoutActuator() {
    ApplicationContextRunner runner =
        new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(MessageDedupAutoConfiguration.class));
    runner
        .withPropertyValues("krizaka.messaging.dedup.store=memory")
        .run(context -> assertThat(context).hasBean("dedupHealthIndicator"));
    runner
        .withPropertyValues(
            "krizaka.messaging.dedup.store=memory", "management.health.dedup.enabled=false")
        .run(context -> assertThat(context).doesNotHaveBean("dedupHealthIndicator"));
    runner.run(context -> assertThat(context).doesNotHaveBean("dedupHealthIndicator"));
    runner
        .withPropertyValues("krizaka.messaging.dedup.store=memory")
        .withClassLoader(new FilteredClassLoader("org.springframework.boot.health"))
        .run(context -> assertThat(context).hasNotFailed().doesNotHaveBean("dedupHealthIndicator"));
  }
}
