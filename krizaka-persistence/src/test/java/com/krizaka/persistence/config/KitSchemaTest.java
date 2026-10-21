package com.krizaka.persistence.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.krizaka.persistence.SchemaSet;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

class KitSchemaTest {

  private static final ClassLoader LOADER = KitSchemaTest.class.getClassLoader();

  private static List<String> declared(MockEnvironment properties) {
    return KitSchema.declared(properties, LOADER).stream().map(SchemaSet::id).toList();
  }

  @Test
  void aSetIsMigratedOnlyWhenItsStoreIsDeclared() {
    assertThat(declared(new MockEnvironment().withProperty("krizaka.app-config.store", "memory")))
        .isEmpty();
    assertThat(
            declared(
                new MockEnvironment()
                    .withProperty("krizaka.messaging.outbox.jdbc.enabled", "true")
                    .withProperty("krizaka.messaging.dedup.store", "jdbc")
                    .withProperty("krizaka.idempotency.store", "jdbc")
                    .withProperty("krizaka.app-config.enabled", "false")))
        .containsExactly("outbox", "message-claims", "idempotency-keys");
  }

  @Test
  void appConfigIsJdbcByDefaultWhenTheModuleIsThere() {
    assertThat(declared(new MockEnvironment())).containsExactly("app-config");
  }

  @Test
  void theTableIsTheServicesOne() {
    List<SchemaSet> sets =
        KitSchema.declared(
            new MockEnvironment()
                .withProperty("krizaka.messaging.dedup.store", "jdbc")
                .withProperty("krizaka.messaging.dedup.table", "billing_claims")
                .withProperty("krizaka.app-config.enabled", "false"),
            LOADER);

    assertThat(sets).singleElement().extracting(SchemaSet::table).isEqualTo("billing_claims");
  }

  @Test
  void theVersionsAreUnique() {
    assertThat(KitSchema.ALL.stream().map(KitSchema::version).distinct())
        .hasSameSizeAs(KitSchema.ALL);
  }
}
