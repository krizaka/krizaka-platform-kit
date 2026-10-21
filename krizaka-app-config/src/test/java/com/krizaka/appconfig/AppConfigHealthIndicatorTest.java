package com.krizaka.appconfig;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.krizaka.appconfig.model.AppConfig;
import com.krizaka.appconfig.model.ClientPlatform;
import com.krizaka.appconfig.store.AppConfigStore;
import com.krizaka.appconfig.store.InMemoryAppConfigStore;
import java.time.Clock;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.Status;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.dao.DataAccessResourceFailureException;

class AppConfigHealthIndicatorTest {

  @Test
  void aStoreThatAnswersIsUpWithItsPlatformsAndTheirMaintenance() {
    InMemoryAppConfigStore store = new InMemoryAppConfigStore(Clock.systemUTC());
    ClientPlatform platform = ClientPlatform.values()[0];
    AppConfig defaults = AppConfig.defaults(platform);
    store.publish(defaults, "test", "first");

    Health health = new AppConfigHealthIndicator(store).health();

    assertThat(health.getStatus()).isEqualTo(Status.UP);
    assertThat(health.getDetails())
        .containsEntry("platforms", List.of(platform.name()))
        .containsEntry("maintenance", List.of());
  }

  @Test
  void aStoreThatDoesNotAnswerIsDown() {
    AppConfigStore store = mock(AppConfigStore.class);
    when(store.findAll()).thenThrow(new DataAccessResourceFailureException("database down"));

    Health health = new AppConfigHealthIndicator(store).health();

    assertThat(health.getStatus()).isEqualTo(Status.DOWN);
    assertThat(health.getDetails())
        .containsEntry("error", DataAccessResourceFailureException.class.getName());
  }

  @Test
  void contributedTurnedOffByPropertyAbsentWithoutActuator() {
    WebApplicationContextRunner runner =
        new WebApplicationContextRunner()
            .withConfiguration(
                AutoConfigurations.of(
                    JacksonAutoConfiguration.class, KrizakaAppConfigAutoConfiguration.class))
            .withPropertyValues("krizaka.app-config.store=memory");
    runner.run(context -> assertThat(context).hasBean("appConfigHealthIndicator"));
    runner
        .withPropertyValues("management.health.app-config.enabled=false")
        .run(context -> assertThat(context).doesNotHaveBean("appConfigHealthIndicator"));
    runner
        .withClassLoader(new FilteredClassLoader("org.springframework.boot.health"))
        .run(
            context ->
                assertThat(context).hasNotFailed().doesNotHaveBean("appConfigHealthIndicator"));
  }
}
