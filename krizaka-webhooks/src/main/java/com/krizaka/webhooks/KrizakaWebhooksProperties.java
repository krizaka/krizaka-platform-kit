package com.krizaka.webhooks;

import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Signed webhooks ({@code krizaka.webhooks}).
 *
 * @param signing How the webhooks this service sends are signed.
 * @param receiving How the webhooks this service receives are verified.
 */
@ConfigurationProperties(prefix = "krizaka.webhooks")
public record KrizakaWebhooksProperties(
    @DefaultValue Signing signing, @DefaultValue Receiving receiving) {

  /** Applies the defaults. */
  public KrizakaWebhooksProperties {
    signing = signing == null ? new Signing(null, null, null, null) : signing;
    receiving = receiving == null ? new Receiving(null, null) : receiving;
  }

  /**
   * Sending ({@code krizaka.webhooks.signing}).
   *
   * @param secret Secret the webhooks this service sends are signed with ('whsec_' + base64, or
   *     plain text). Setting it contributes the WebhookSender.
   * @param previousSecret Secret being rotated out: during a rotation both sign, and receivers that
   *     still hold the old one keep accepting.
   * @param connectTimeout How long an attempt waits for the receiver's connection.
   * @param readTimeout How long an attempt waits for the receiver's answer.
   */
  public record Signing(
      String secret,
      String previousSecret,
      @DefaultValue("2s") Duration connectTimeout,
      @DefaultValue("10s") Duration readTimeout) {

    /** Applies the defaults. */
    public Signing {
      connectTimeout = connectTimeout == null ? Duration.ofSeconds(2) : connectTimeout;
      readTimeout = readTimeout == null ? Duration.ofSeconds(10) : readTimeout;
    }

    @Override
    public String toString() {
      return "Signing[secret=<redacted>, previousSecret=<redacted>, connectTimeout=%s, readTimeout=%s]"
          .formatted(connectTimeout, readTimeout);
    }
  }

  /**
   * Receiving ({@code krizaka.webhooks.receiving}).
   *
   * @param secrets Secrets a received webhook may be signed with, the current one first. Setting
   *     one contributes the WebhookVerifier.
   * @param tolerance How far a webhook's timestamp may be from now, either way; an older capture
   *     replayed is refused.
   */
  public record Receiving(List<String> secrets, @DefaultValue("5m") Duration tolerance) {

    /** Applies the defaults. */
    public Receiving {
      secrets = secrets == null ? List.of() : List.copyOf(secrets);
      tolerance = tolerance == null ? Duration.ofMinutes(5) : tolerance;
    }

    @Override
    public String toString() {
      return "Receiving[secrets=<redacted>, tolerance=%s]".formatted(tolerance);
    }
  }
}
