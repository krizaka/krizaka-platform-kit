package com.krizaka.messaging.consume;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class ConsumerRetryPropertiesTest {

  @Test
  void defaultsAreFiveAttemptsFromHalfASecondDoublingUpToTenSeconds() {
    ConsumerRetryProperties retry = ConsumerRetryProperties.defaults();

    assertThat(retry.maxAttempts()).isEqualTo(5);
    assertThat(retry.initial()).isEqualTo(Duration.ofMillis(500));
    assertThat(retry.multiplier()).isEqualTo(2.0);
    assertThat(retry.max()).isEqualTo(Duration.ofSeconds(10));
  }

  @Test
  void oneAttemptMeansNoRetry() {
    assertThat(new ConsumerRetryProperties(1, null, null, null).maxAttempts()).isEqualTo(1);
  }

  @Test
  void refusesAPolicyThatCannotRun() {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new ConsumerRetryProperties(0, null, null, null));
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new ConsumerRetryProperties(null, Duration.ZERO, null, null));
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new ConsumerRetryProperties(null, null, 0.5, null));
    assertThatIllegalArgumentException()
        .isThrownBy(
            () ->
                new ConsumerRetryProperties(
                    null, Duration.ofSeconds(5), null, Duration.ofSeconds(1)))
        .withMessageContaining("max");
  }
}
