package com.krizaka.webhooks;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class KrizakaWebhooksAutoConfigurationTest {

  private final ApplicationContextRunner runner =
      new ApplicationContextRunner()
          .withConfiguration(AutoConfigurations.of(KrizakaWebhooksAutoConfiguration.class));

  @Test
  void nothingWithoutASecret() {
    runner.run(
        context ->
            assertThat(context)
                .doesNotHaveBean(WebhookSender.class)
                .doesNotHaveBean(WebhookVerifier.class));
  }

  @Test
  void aSigningSecretContributesTheSenderOnly() {
    runner
        .withPropertyValues("krizaka.webhooks.signing.secret=s3cret")
        .run(
            context ->
                assertThat(context)
                    .hasSingleBean(WebhookSender.class)
                    .doesNotHaveBean(WebhookVerifier.class));
  }

  @Test
  void aYamlListOfReceivingSecretsContributesTheVerifier() {
    runner
        .withPropertyValues(
            "krizaka.webhooks.receiving.secrets[0]=one",
            "krizaka.webhooks.receiving.secrets[1]=two")
        .run(
            context ->
                assertThat(context)
                    .hasSingleBean(WebhookVerifier.class)
                    .doesNotHaveBean(WebhookSender.class));
  }

  @Test
  void aCommaSeparatedValueContributesTheVerifierToo() {
    runner
        .withPropertyValues("krizaka.webhooks.receiving.secrets=one,two")
        .run(context -> assertThat(context).hasSingleBean(WebhookVerifier.class));
  }

  @Test
  void aBlankSecretContributesNothing() {
    runner
        .withPropertyValues("krizaka.webhooks.receiving.secrets= ")
        .run(context -> assertThat(context).doesNotHaveBean(WebhookVerifier.class));
  }

  @Test
  void theApplicationsOwnBeansWin() {
    WebhookVerifier own =
        new WebhookVerifier(
            java.util.List.of("mine"),
            Duration.ofMinutes(1),
            java.time.Clock.systemUTC(),
            io.micrometer.observation.ObservationRegistry.NOOP);
    runner
        .withPropertyValues("krizaka.webhooks.receiving.secrets=one")
        .withBean(WebhookVerifier.class, () -> own)
        .run(context -> assertThat(context.getBean(WebhookVerifier.class)).isSameAs(own));
  }

  @Test
  void theDefaultsAreTheDocumentedOnes() {
    runner.run(
        context -> {
          var properties = context.getBean(KrizakaWebhooksProperties.class);
          assertThat(properties.receiving().tolerance()).isEqualTo(Duration.ofMinutes(5));
          assertThat(properties.signing().connectTimeout()).isEqualTo(Duration.ofSeconds(2));
          assertThat(properties.signing().readTimeout()).isEqualTo(Duration.ofSeconds(10));
        });
  }

  @Test
  void secretsNeverReachAToString() {
    runner
        .withPropertyValues(
            "krizaka.webhooks.signing.secret=sign-me",
            "krizaka.webhooks.receiving.secrets=verify-me")
        .run(
            context ->
                assertThat(context.getBean(KrizakaWebhooksProperties.class).toString())
                    .doesNotContain("sign-me")
                    .doesNotContain("verify-me"));
  }
}
