package com.orazaka.notificationservice.infrastructure.config;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Wiring of the notification service ({@code orazaka.notifications}): who the mail is from, where
 * the templates are, which links the identity templates point at, and the providers behind the SMS
 * and webhook channels. Infrastructure only (AGENTS.md §4) — no message content lives here.
 *
 * @param fromAddress sender address of every e-mail
 * @param templateLocation Spring resource location of the templates, ending with {@code /}
 * @param links the application URLs the identity e-mails link to
 * @param twilio the SMS provider; blank account sid = SMS not configured
 * @param webhook the webhook channel's allow-list
 */
@ConfigurationProperties(prefix = "orazaka.notifications")
public record NotificationProperties(
    String fromAddress, String templateLocation, Links links, Twilio twilio, Webhook webhook) {

  public NotificationProperties {
    if (fromAddress == null || fromAddress.isBlank()) {
      throw new IllegalArgumentException("orazaka.notifications.from-address is required");
    }
    templateLocation =
        (templateLocation == null || templateLocation.isBlank())
            ? "classpath:templates/"
            : (templateLocation.endsWith("/") ? templateLocation : templateLocation + "/");
    Objects.requireNonNull(links, "orazaka.notifications.links is required");
    twilio = (twilio == null) ? new Twilio(null, null, null, null) : twilio;
    webhook = (webhook == null) ? new Webhook(List.of()) : webhook;
  }

  /**
   * Application URLs of the identity flows; {@code {token}} is replaced by the URL-encoded token.
   *
   * @param verifyEmail the e-mail verification page
   * @param resetPassword the password reset page
   */
  public record Links(String verifyEmail, String resetPassword) {

    public Links {
      requireTokenPlaceholder(verifyEmail, "verify-email");
      requireTokenPlaceholder(resetPassword, "reset-password");
    }

    /** The verification link carrying {@code token}. */
    public String verifyEmailUrl(String token) {
      return verifyEmail.replace("{token}", encode(token));
    }

    /** The reset link carrying {@code token}. */
    public String resetPasswordUrl(String token) {
      return resetPassword.replace("{token}", encode(token));
    }

    private static void requireTokenPlaceholder(String url, String name) {
      if (url == null || !url.contains("{token}")) {
        throw new IllegalArgumentException(
            "orazaka.notifications.links." + name + " must contain {token}");
      }
    }

    private static String encode(String token) {
      return URLEncoder.encode(token, StandardCharsets.UTF_8);
    }
  }

  /**
   * Twilio Messages API credentials.
   *
   * @param accountSid account SID; blank disables the SMS channel
   * @param authToken auth token
   * @param fromNumber sending number (E.164)
   * @param baseUrl API base URL
   */
  public record Twilio(String accountSid, String authToken, String fromNumber, String baseUrl) {

    public Twilio {
      baseUrl = (baseUrl == null || baseUrl.isBlank()) ? "https://api.twilio.com" : baseUrl;
    }

    /** {@code true} when every credential needed to send is present. */
    public boolean configured() {
      return accountSid != null
          && !accountSid.isBlank()
          && authToken != null
          && !authToken.isBlank()
          && fromNumber != null
          && !fromNumber.isBlank();
    }
  }

  /**
   * Webhook channel policy. Only hosts on the allow-list are called: a recipient URL arrives in a
   * message, and without the list any producer could make this service call any address.
   *
   * @param allowedHosts host names a webhook may target; empty disables the channel
   */
  public record Webhook(List<String> allowedHosts) {

    public Webhook {
      allowedHosts =
          (allowedHosts == null)
              ? List.of()
              : allowedHosts.stream()
                  .filter(host -> host != null && !host.isBlank())
                  .map(host -> host.strip().toLowerCase(Locale.ROOT))
                  .toList();
    }

    /** {@code true} when {@code host} is on the allow-list. */
    public boolean allows(String host) {
      return host != null && allowedHosts.contains(host.toLowerCase(Locale.ROOT));
    }
  }
}
