package com.orazaka.notificationservice.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class ClockConfigTest {

  @Test
  void providesAUtcClock() {
    assertThat(new ClockConfig().clock().getZone()).isEqualTo(ZoneOffset.UTC);
  }
}
