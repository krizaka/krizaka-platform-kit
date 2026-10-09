package com.krizaka.observability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import org.junit.jupiter.api.Test;

class KrizakaObservabilityPropertiesTest {

  @Test
  void keepsTheThreeNames() {
    KrizakaObservabilityProperties properties =
        new KrizakaObservabilityProperties("acme", "orders", "1.2.3");

    assertThat(properties.product()).isEqualTo("acme");
    assertThat(properties.service()).isEqualTo("orders");
    assertThat(properties.version()).isEqualTo("1.2.3");
  }

  @Test
  void namesEveryMissingProperty() {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new KrizakaObservabilityProperties(null, " ", "1.2.3"))
        .withMessageContaining("krizaka.observability.product")
        .withMessageContaining("krizaka.observability.service")
        .withMessageNotContaining("krizaka.observability.version");
  }

  @Test
  void refusesABlankVersion() {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new KrizakaObservabilityProperties("acme", "orders", ""))
        .withMessageContaining("krizaka.observability.version");
  }
}
