package com.krizaka.appconfig.store;

import static org.assertj.core.api.Assertions.assertThat;

import com.krizaka.appconfig.model.AppConfig;
import com.krizaka.appconfig.model.AppConfigChange;
import com.krizaka.appconfig.model.AppConfigUpdate;
import com.krizaka.appconfig.model.AppVersion;
import com.krizaka.appconfig.model.ClientPlatform;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class AppConfigReaderTest {

  /** A store that counts reads and can fail. */
  static final class Flaky implements AppConfigStore {
    final InMemoryAppConfigStore inner;
    final AtomicInteger reads = new AtomicInteger();
    boolean failing;

    Flaky(Clock clock) {
      inner = new InMemoryAppConfigStore(clock);
    }

    @Override
    public Optional<AppConfig> find(ClientPlatform platform) {
      reads.incrementAndGet();
      if (failing) {
        throw new IllegalStateException("database down");
      }
      return inner.find(platform);
    }

    @Override
    public List<AppConfig> findAll() {
      return inner.findAll();
    }

    @Override
    public AppConfig publish(AppConfig config, String actor, String reason) {
      return inner.publish(config, actor, reason);
    }

    @Override
    public List<AppConfigChange> history(ClientPlatform platform) {
      return inner.history(platform);
    }

    @Override
    public Optional<AppConfig> rollback(
        ClientPlatform platform, int revision, String actor, String reason) {
      return inner.rollback(platform, revision, actor, reason);
    }
  }

  /** A clock the test moves. */
  static final class Moving extends Clock {
    Instant now = Instant.parse("2026-10-16T12:00:00Z");

    @Override
    public java.time.ZoneId getZone() {
      return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(java.time.ZoneId zone) {
      return this;
    }

    @Override
    public Instant instant() {
      return now;
    }
  }

  private static AppConfig forcing(String min) {
    return new AppConfigUpdate(min, min, null, null, false, null, null, 1, 1, null, null, "r")
        .toConfig(ClientPlatform.IOS);
  }

  @Test
  void readsTheStoreOncePerTtl() {
    Moving clock = new Moving();
    Flaky store = new Flaky(clock);
    AppConfigReader reader = new AppConfigReader(store, Duration.ofSeconds(30), clock);
    reader.get(ClientPlatform.IOS);
    reader.get(ClientPlatform.IOS);
    assertThat(store.reads).hasValue(1);
    clock.now = clock.now.plusSeconds(31);
    reader.get(ClientPlatform.IOS);
    assertThat(store.reads).hasValue(2);
  }

  @Test
  void aPublicationIsVisibleAtOnce() {
    Moving clock = new Moving();
    Flaky store = new Flaky(clock);
    AppConfigReader reader = new AppConfigReader(store, Duration.ofMinutes(5), clock);
    assertThat(reader.get(ClientPlatform.IOS).minSupportedVersion()).isEqualTo(AppVersion.ZERO);
    reader.publish(forcing("2.0.0"), "admin", "ship 2.0");
    assertThat(reader.get(ClientPlatform.IOS).minSupportedVersion())
        .isEqualTo(AppVersion.of("2.0.0"));
  }

  @Test
  void aStoreOutageServesTheLastKnownValue() {
    Moving clock = new Moving();
    Flaky store = new Flaky(clock);
    AppConfigReader reader = new AppConfigReader(store, Duration.ofSeconds(1), clock);
    reader.publish(forcing("2.0.0"), "admin", "ship 2.0");
    reader.get(ClientPlatform.IOS);
    store.failing = true;
    clock.now = clock.now.plusSeconds(5);
    assertThat(reader.get(ClientPlatform.IOS).minSupportedVersion())
        .isEqualTo(AppVersion.of("2.0.0"));
  }

  @Test
  void aStoreOutageWithNothingKnownLocksNobodyOut() {
    Moving clock = new Moving();
    Flaky store = new Flaky(clock);
    store.failing = true;
    AppConfigReader reader = new AppConfigReader(store, Duration.ofSeconds(1), clock);
    AppConfig served = reader.get(ClientPlatform.ANDROID);
    assertThat(served.minSupportedVersion()).isEqualTo(AppVersion.ZERO);
    assertThat(served.maintenanceOn()).isFalse();
  }

  @Test
  void historyAndRollbackRecordWhoAndWhy() {
    Moving clock = new Moving();
    Flaky store = new Flaky(clock);
    AppConfigReader reader = new AppConfigReader(store, Duration.ZERO, clock);
    reader.publish(forcing("1.0.0"), "ana", "first");
    reader.publish(forcing("2.0.0"), "bob", "force 2.0");
    Optional<AppConfig> restored = reader.rollback(ClientPlatform.IOS, 1, "ana", "2.0 was bad");
    assertThat(restored).isPresent();
    assertThat(restored.get().revision()).isEqualTo(3);
    assertThat(restored.get().minSupportedVersion()).isEqualTo(AppVersion.of("1.0.0"));
    List<AppConfigChange> trail = store.history(ClientPlatform.IOS);
    assertThat(trail)
        .extracting(AppConfigChange::reason)
        .containsExactly("2.0 was bad", "force 2.0", "first");
    assertThat(trail).extracting(AppConfigChange::changedBy).containsExactly("ana", "bob", "ana");
    assertThat(reader.rollback(ClientPlatform.IOS, 99, "ana", "none")).isEmpty();
  }

  @Test
  void aPublicationWithoutAReasonIsRefused() {
    Moving clock = new Moving();
    Flaky store = new Flaky(clock);
    AppConfigReader reader = new AppConfigReader(store, Duration.ZERO, clock);
    org.assertj.core.api.Assertions.assertThatThrownBy(
            () -> reader.publish(forcing("1.0.0"), "ana", " "))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("reason");
  }
}
