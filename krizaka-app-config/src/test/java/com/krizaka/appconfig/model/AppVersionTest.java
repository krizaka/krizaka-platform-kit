package com.krizaka.appconfig.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class AppVersionTest {

  @ParameterizedTest
  @ValueSource(strings = {"1.4.0", "1.4", "v1.4.0", "1.4.0-beta.2", "1.4.0+42", " 1.4.0 "})
  void readsTheNumericCore(String text) {
    assertThat(AppVersion.parse(text)).contains(new AppVersion(1, 4, 0));
  }

  @ParameterizedTest
  @ValueSource(strings = {"", "abc", "-1.0.0", "1.x.0", "latest"})
  void aStringThatIsNotAVersionIsEmpty(String text) {
    assertThat(AppVersion.parse(text)).isEmpty();
  }

  @Test
  void nullIsEmpty() {
    assertThat(AppVersion.parse(null)).isEmpty();
  }

  @Test
  void comparesNumericallyNotAsText() {
    assertThat(AppVersion.of("1.9.0").isBefore(AppVersion.of("1.10.0"))).isTrue();
    assertThat(AppVersion.of("2.0.0").isBefore(AppVersion.of("1.99.99"))).isFalse();
    assertThat(AppVersion.of("1.2.3")).isEqualByComparingTo(AppVersion.of("1.2.3"));
  }

  @Test
  void ofRejectsWhatIsNotAVersion() {
    assertThatThrownBy(() -> AppVersion.of("nope")).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void printsItsCanonicalForm() {
    assertThat(AppVersion.of("v1.4")).hasToString("1.4.0");
  }
}
