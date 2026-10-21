package com.krizaka.webhooks;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import java.time.Clock;
import java.util.Objects;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Sends one signed webhook attempt: a JSON body with the Standard Webhooks headers, signed with the
 * current secret and, during a rotation, the previous one.
 *
 * <p>Keep the message id stable across the retries of one delivery: the receiver dedupes on it.
 * Retrying (and remembering what to retry) is the caller's: put the deliveries in the outbox, or in
 * a table of your own, and call {@link #send} from the job that sweeps it. Observed as {@value
 * #OBSERVATION}, tagged with the outcome ({@code delivered}, {@code refused}, {@code unreachable}).
 */
public final class WebhookSender {

  /** The observation of one attempt. */
  public static final String OBSERVATION = "krizaka.webhooks.send";

  private final RestClient http;
  private final String secret;
  private final String previousSecret;
  private final Clock clock;
  private final ObservationRegistry observations;

  /**
   * A sender.
   *
   * @param http the client attempts go through (its timeouts are the attempt's)
   * @param secret the current secret
   * @param previousSecret the secret being rotated out, or {@code null}
   * @param clock the clock
   * @param observations where attempts are observed
   */
  public WebhookSender(
      RestClient http,
      String secret,
      String previousSecret,
      Clock clock,
      ObservationRegistry observations) {
    this.http = Objects.requireNonNull(http, "http");
    WebhookSignature.key(secret);
    this.secret = secret;
    this.previousSecret =
        previousSecret == null || previousSecret.isBlank() ? null : previousSecret;
    this.clock = Objects.requireNonNull(clock, "clock");
    this.observations = Objects.requireNonNull(observations, "observations");
  }

  /**
   * The outcome of an attempt.
   *
   * @param status the receiver's status, or {@code null} when it could not be reached
   * @param error why it was not delivered, or {@code null}
   */
  public record Attempt(Integer status, String error) {
    /**
     * Whether the receiver accepted it (2xx).
     *
     * @return {@code true} on a 2xx
     */
    public boolean delivered() {
      return status != null && status >= 200 && status < 300;
    }
  }

  /**
   * Sends one attempt, never throws for the receiver's answer or absence.
   *
   * @param url the receiver
   * @param id the message id, stable across retries ({@code msg_…})
   * @param body the JSON body, sent exactly as signed
   * @return the outcome
   */
  public Attempt send(String url, String id, String body) {
    Objects.requireNonNull(url, "url");
    Objects.requireNonNull(id, "id");
    Objects.requireNonNull(body, "body");
    Observation observation = Observation.createNotStarted(OBSERVATION, observations).start();
    try {
      var headers =
          WebhookSignature.headers(
              secret, previousSecret, id, clock.instant().getEpochSecond(), body);
      HttpStatusCode status =
          http.post()
              .uri(url)
              .contentType(MediaType.APPLICATION_JSON)
              .headers(h -> headers.forEach(h::set))
              .body(body)
              .retrieve()
              .onStatus(code -> true, (request, response) -> {})
              .toBodilessEntity()
              .getStatusCode();
      Attempt attempt = new Attempt(status.value(), null);
      observation.lowCardinalityKeyValue("outcome", attempt.delivered() ? "delivered" : "refused");
      return attempt.delivered()
          ? attempt
          : new Attempt(status.value(), "the receiver answered " + status.value());
    } catch (RestClientException unreachable) {
      observation.lowCardinalityKeyValue("outcome", "unreachable");
      observation.error(unreachable);
      return new Attempt(null, "not delivered: " + unreachable.getClass().getSimpleName());
    } finally {
      observation.stop();
    }
  }
}
