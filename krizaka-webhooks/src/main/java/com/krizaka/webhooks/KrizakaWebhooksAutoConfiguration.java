package com.krizaka.webhooks;

import io.micrometer.observation.ObservationRegistry;
import java.time.Clock;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * Contributes the {@link WebhookSender} when {@code krizaka.webhooks.signing.secret} is set, and
 * the {@link WebhookVerifier} when {@code krizaka.webhooks.receiving.secrets} lists one. Each backs
 * off when the application declares its own.
 */
@AutoConfiguration(
    afterName = "org.springframework.boot.restclient.autoconfigure.RestClientAutoConfiguration")
@EnableConfigurationProperties(KrizakaWebhooksProperties.class)
public class KrizakaWebhooksAutoConfiguration {

  /** Creates the auto-configuration. */
  public KrizakaWebhooksAutoConfiguration() {}

  /**
   * The sender, its timeouts the properties'.
   *
   * @param builder the application's RestClient builder (its observations), when it has one
   * @param observations where attempts are observed, when Micrometer is configured
   * @param properties the secrets and the timeouts
   * @return the sender
   */
  @Bean
  @ConditionalOnMissingBean
  @ConditionalOnProperty(prefix = "krizaka.webhooks.signing", name = "secret")
  public WebhookSender webhookSender(
      ObjectProvider<RestClient.Builder> builder,
      ObjectProvider<ObservationRegistry> observations,
      KrizakaWebhooksProperties properties) {
    SimpleClientHttpRequestFactory timeouts = new SimpleClientHttpRequestFactory();
    timeouts.setConnectTimeout(properties.signing().connectTimeout());
    timeouts.setReadTimeout(properties.signing().readTimeout());
    return new WebhookSender(
        builder.getIfAvailable(RestClient::builder).requestFactory(timeouts).build(),
        properties.signing().secret(),
        properties.signing().previousSecret(),
        Clock.systemUTC(),
        observations.getIfAvailable(() -> ObservationRegistry.NOOP));
  }

  /**
   * The verifier.
   *
   * @param observations where verifications are observed, when Micrometer is configured
   * @param properties the secrets and the tolerance
   * @return the verifier
   */
  @Bean
  @ConditionalOnMissingBean
  @Conditional(OnReceivingSecretsCondition.class)
  public WebhookVerifier webhookVerifier(
      ObjectProvider<ObservationRegistry> observations, KrizakaWebhooksProperties properties) {
    return new WebhookVerifier(
        properties.receiving().secrets(),
        properties.receiving().tolerance(),
        Clock.systemUTC(),
        observations.getIfAvailable(() -> ObservationRegistry.NOOP));
  }
}
