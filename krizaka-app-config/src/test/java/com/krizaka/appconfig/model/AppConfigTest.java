package com.krizaka.appconfig.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AppConfigTest {

  private static AppConfig with(
      String min,
      String latest,
      String storeUrl,
      int contractMin,
      int contractMax,
      Map<String, Object> flags,
      Map<String, String> message) {
    return new AppConfig(
        ClientPlatform.IOS,
        AppVersion.of(min),
        AppVersion.of(latest),
        null,
        storeUrl,
        false,
        Map.of(),
        message,
        contractMin,
        contractMax,
        null,
        flags,
        1,
        Instant.EPOCH,
        "test");
  }

  @Test
  void theDefaultsLockNobodyOut() {
    AppConfig defaults = AppConfig.defaults(ClientPlatform.ANDROID);
    assertThat(
            AppConfigPolicy.verdict(defaults, AppVersion.of("0.0.1"), java.util.OptionalInt.of(1)))
        .isEqualTo(AppConfigPolicy.Verdict.OK);
    assertThat(defaults.maintenanceOn()).isFalse();
  }

  @Test
  void theMinimumCannotExceedTheLatestVersion() {
    assertThatThrownBy(() -> with("2.0.0", "1.0.0", null, 1, 1, Map.of(), Map.of()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("older than minSupportedVersion");
  }

  @Test
  void theContractRangeCannotBeEmpty() {
    assertThatThrownBy(() -> with("1.0.0", "1.0.0", null, 3, 2, Map.of(), Map.of()))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> with("1.0.0", "1.0.0", null, 0, 1, Map.of(), Map.of()))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void aStoreUrlMustBeHttps() {
    assertThatThrownBy(
            () -> with("1.0.0", "1.0.0", "http://x.example/app", 1, 1, Map.of(), Map.of()))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () -> with("1.0.0", "1.0.0", "javascript:alert(1)", 1, 1, Map.of(), Map.of()))
        .isInstanceOf(IllegalArgumentException.class);
    assertThat(with("1.0.0", "1.0.0", " ", 1, 1, Map.of(), Map.of()).storeUrl()).isNull();
  }

  @Test
  void flagsAreScalarsWithSafeKeys() {
    assertThatThrownBy(() -> with("1.0.0", "1.0.0", null, 1, 1, Map.of("a", Map.of()), Map.of()))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> with("1.0.0", "1.0.0", null, 1, 1, Map.of("bad key", true), Map.of()))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void textsAreTwoLetterLanguagesAndBounded() {
    assertThatThrownBy(() -> with("1.0.0", "1.0.0", null, 1, 1, Map.of(), Map.of("english", "x")))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () -> with("1.0.0", "1.0.0", null, 1, 1, Map.of(), Map.of("en", "x".repeat(501))))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
