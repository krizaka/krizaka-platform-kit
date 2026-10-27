package com.krizaka.example.webhooks;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** Sonar S6718: the example's configuration carries no webhook secret, it reads one from the environment. */
class NoSecretInConfigTest {

  @Test
  void applicationYmlHoldsNoWebhookSecret() throws Exception {
    String yml = Files.readString(Path.of("src/main/resources/application.yml"));
    assertThat(yml).doesNotContain("whsec_").contains("${APP_WEBHOOKS_SECRET}");
  }
}
