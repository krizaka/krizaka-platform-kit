package com.krizaka.webhooks;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

/**
 * Verifies a received webhook the Standard Webhooks way, before its body is trusted.
 *
 * <pre>{@code
 * @PostMapping("/webhooks/payments")
 * ResponseEntity<Void> payments(@RequestHeader HttpHeaders headers, @RequestBody String body) {
 *   verifier.verify(headers::getFirst, body);   // 401 invalid-webhook otherwise
 *   payments.settle(json.readValue(body, PaymentSettled.class));
 *   return ResponseEntity.noContent().build();
 * }
 * }</pre>
 *
 * <ul>
 *   <li>the three headers are present;
 *   <li>the timestamp is within {@code tolerance} of now, either way — a replayed capture is
 *       refused;
 *   <li>one of the {@code v1} signatures matches one of the secrets (the current one and the one
 *       being rotated out), compared in constant time.
 * </ul>
 *
 * <p>The body is verified exactly as received: read it as a {@code String}, never re-serialize what
 * was parsed. Dedupe on {@code webhook-id}: a sender may deliver one message twice. Observed as
 * {@value #OBSERVATION} with {@code outcome} {@code valid} or {@code invalid}.
 */
public final class WebhookVerifier {

  /** The observation of one verification. */
  public static final String OBSERVATION = "krizaka.webhooks.verify";

  private final List<String> secrets;
  private final Duration tolerance;
  private final Clock clock;
  private final ObservationRegistry observations;

  /**
   * A verifier.
   *
   * @param secrets the secrets accepted, the current one first; at least one
   * @param tolerance how far the timestamp may be from now
   * @param clock the clock
   * @param observations where verifications are observed
   */
  public WebhookVerifier(
      List<String> secrets, Duration tolerance, Clock clock, ObservationRegistry observations) {
    if (secrets == null || secrets.isEmpty()) {
      throw new IllegalArgumentException("a webhook verifier needs at least one secret");
    }
    secrets.forEach(WebhookSignature::key);
    this.secrets = List.copyOf(secrets);
    this.tolerance = Objects.requireNonNull(tolerance, "tolerance");
    this.clock = Objects.requireNonNull(clock, "clock");
    this.observations = Objects.requireNonNull(observations, "observations");
  }

  /**
   * Verifies a webhook, or refuses it.
   *
   * @param header the request's headers by name ({@code headers::getFirst})
   * @param body the body exactly as received
   * @return the message id, to dedupe on
   * @throws InvalidWebhookException when a check fails
   */
  public String verify(Function<String, String> header, String body) {
    Observation observation = Observation.createNotStarted(OBSERVATION, observations).start();
    try {
      String id = check(header, body);
      observation.lowCardinalityKeyValue("outcome", "valid");
      return id;
    } catch (InvalidWebhookException invalid) {
      observation.lowCardinalityKeyValue("outcome", "invalid");
      throw invalid;
    } finally {
      observation.stop();
    }
  }

  private String check(Function<String, String> header, String body) {
    String id = header.apply(WebhookSignature.ID);
    String timestamp = header.apply(WebhookSignature.TIMESTAMP);
    String signatures = header.apply(WebhookSignature.SIGNATURE);
    if (blank(id) || blank(timestamp) || blank(signatures)) {
      throw new InvalidWebhookException(
          "The webhook-id, webhook-timestamp and webhook-signature headers are required.");
    }
    long seconds;
    try {
      seconds = Long.parseLong(timestamp.strip());
    } catch (NumberFormatException e) {
      throw new InvalidWebhookException("The webhook-timestamp is not a number of seconds.");
    }
    long skew = Math.abs(clock.instant().getEpochSecond() - seconds);
    if (skew > tolerance.toSeconds()) {
      throw new InvalidWebhookException("The webhook-timestamp is outside the tolerance.");
    }
    String content = body == null ? "" : body;
    for (String secret : secrets) {
      byte[] expected =
          WebhookSignature.sign(secret, id, seconds, content).getBytes(StandardCharsets.UTF_8);
      for (String candidate : signatures.strip().split("\\s+")) {
        if (candidate.startsWith("v1,")
            && MessageDigest.isEqual(expected, candidate.getBytes(StandardCharsets.UTF_8))) {
          return id;
        }
      }
    }
    throw new InvalidWebhookException("No webhook-signature matches.");
  }

  private static boolean blank(String value) {
    return value == null || value.isBlank();
  }
}
