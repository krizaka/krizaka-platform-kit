package com.krizaka.messaging.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class OutboxPropertiesTest {

  @Test
  void defaults() {
    OutboxProperties properties = new OutboxProperties(null, null, null, null, null);

    assertThat(properties.enabled()).isTrue();
    assertThat(properties.pollInterval()).isEqualTo(Duration.ofMillis(500));
    assertThat(properties.batchSize()).isEqualTo(100);
    assertThat(properties.purgeInterval()).isEqualTo(Duration.ofHours(1));
    assertThat(properties.retention()).isEqualTo(Duration.ofDays(7));
  }

  @Test
  void refusesNonPositiveValues() {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new OutboxProperties(true, Duration.ZERO, null, null, null));
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new OutboxProperties(true, null, 0, null, null));
  }
}
