package com.krizaka.messaging.dedup;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.krizaka.messaging.RecordedObservations;
import io.micrometer.observation.ObservationRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class ObservedMessageDedupTest {

  private final RecordedObservations observations = new RecordedObservations();
  private final MessageDedup dedup =
      ObservedMessageDedup.of(new InMemoryMessageDedup(), observations.registry());

  @Test
  void eachOutcomeIsObservedWithItsConsumer() {
    dedup.processOnce("notifications.user-events", "m-1", () -> {});
    dedup.processOnce("notifications.user-events", "m-1", () -> {});
    assertThatThrownBy(
            () ->
                dedup.processOnce(
                    "notifications.user-events",
                    "m-2",
                    () -> {
                      throw new IllegalStateException("handler failed");
                    }))
        .hasMessage("handler failed");

    var stopped = observations.named(ObservedMessageDedup.OBSERVATION);
    assertThat(stopped)
        .extracting(s -> s.low().get("outcome"))
        .containsExactly("processed", "duplicate", "failed");
    assertThat(stopped.getFirst().low())
        .containsEntry("krizaka.consumer", "notifications.user-events");
    assertThat(stopped.getLast().error()).isInstanceOf(IllegalStateException.class);
  }

  @Test
  void aFailureStillReleasesTheClaim() {
    assertThatThrownBy(
            () ->
                dedup.processOnce(
                    "c",
                    "m-3",
                    () -> {
                      throw new IllegalStateException("x");
                    }))
        .isInstanceOf(IllegalStateException.class);

    assertThat(dedup.processOnce("c", "m-3", () -> {})).isTrue();
  }

  @Test
  void withoutARegistryNothingIsWrapped() {
    InMemoryMessageDedup raw = new InMemoryMessageDedup();
    assertThat(ObservedMessageDedup.of(raw, null)).isSameAs(raw);
    assertThat(ObservedMessageDedup.of(raw, ObservationRegistry.NOOP)).isSameAs(raw);
  }

  @Test
  void theAutoConfiguredStoreIsObservedWhenTheApplicationHasARegistry() {
    new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(MessageDedupAutoConfiguration.class))
        .withPropertyValues("krizaka.messaging.dedup.store=memory")
        .withBean(ObservationRegistry.class, observations::registry)
        .run(
            context -> {
              MessageDedup bean = context.getBean(MessageDedup.class);
              assertThat(bean).isInstanceOf(ObservedMessageDedup.class);
              assertThat(((ObservedMessageDedup) bean).delegate())
                  .isInstanceOf(InMemoryMessageDedup.class);
            });
  }
}
