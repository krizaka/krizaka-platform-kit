package com.krizaka.idempotency;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

/**
 * Runs an action at most once per {@code Idempotency-Key}.
 *
 * <pre>{@code
 * @PostMapping("/api/v1/orders")
 * ResponseEntity<Order> create(@RequestBody NewOrder order,
 *     @RequestHeader(value = IdempotencyKeys.KEY_HEADER, required = false) String key,
 *     @AuthenticationPrincipal Jwt caller) {
 *   return idempotency
 *       .execute(new IdempotentRequest("orders.create", caller.getSubject(), key, order),
 *           Order.class, () -> orders.create(order))
 *       .toResponse(HttpStatus.CREATED);
 * }
 * }</pre>
 *
 * <ul>
 *   <li>the first request with a key runs the action and stores its response, in the action's
 *       transaction (JDBC store): an action that fails records nothing, and the retry runs for
 *       real;
 *   <li>a retry with the same key and the same request replays the stored response, flagged {@code
 *       Idempotent-Replayed: true} — the action does not run again;
 *   <li>the same key with a different request is {@code 422 idempotency-key-reused};
 *   <li>two concurrent requests with one key take turns: the second waits, then replays;
 *   <li>no key runs the action as if this class were not there; a malformed key is {@code 400
 *       invalid-idempotency-key}.
 * </ul>
 *
 * <p>Observed as {@value #OBSERVATION}, tagged with the scope and the outcome ({@code executed},
 * {@code replayed}, {@code rejected}, {@code failed}).
 */
public final class Idempotency {

  /** The observation of one {@link #execute}. */
  public static final String OBSERVATION = "krizaka.idempotency";

  private final IdempotencyStore store;
  private final JsonMapper json;
  private final RequestFingerprint fingerprints;
  private final TransactionTemplate transactions;
  private final Duration ttl;
  private final Clock clock;
  private final ObservationRegistry observations;

  /**
   * The service.
   *
   * @param store where keys are kept
   * @param json how responses are stored and read back (the application's mapper)
   * @param transactions the transaction a claim, the action and its response share; required by a
   *     {@linkplain IdempotencyStore#transactional() transactional} store, {@code null} otherwise
   * @param ttl how long a key replays; after it, the key runs again
   * @param clock the clock
   * @param observations where executions are observed; {@link ObservationRegistry#NOOP} for none
   */
  public Idempotency(
      IdempotencyStore store,
      JsonMapper json,
      TransactionTemplate transactions,
      Duration ttl,
      Clock clock,
      ObservationRegistry observations) {
    this.store = Objects.requireNonNull(store, "store");
    this.json = Objects.requireNonNull(json, "json");
    this.fingerprints = new RequestFingerprint(json);
    if (store.transactional() && transactions == null) {
      throw new IllegalArgumentException(
          store.getClass().getSimpleName() + " joins a transaction: give it a TransactionTemplate");
    }
    this.transactions = transactions;
    this.ttl = Objects.requireNonNull(ttl, "ttl");
    this.clock = Objects.requireNonNull(clock, "clock");
    this.observations = Objects.requireNonNull(observations, "observations");
  }

  /**
   * Runs the action once for this key, or replays its response.
   *
   * @param request the scope, the subject, the key and the request
   * @param responseType the type a replayed response is read back as
   * @param action the work; it runs at most once per key while the key is kept
   * @param <T> the response type
   * @return the response and whether it is a replay
   * @throws InvalidIdempotencyKeyException when the key is not 1 to 255 visible ASCII characters
   * @throws IdempotencyKeyReusedException when the key ran a different request
   */
  public <T> IdempotentOutcome<T> execute(
      IdempotentRequest request, Class<T> responseType, Supplier<T> action) {
    Objects.requireNonNull(request, "request");
    Objects.requireNonNull(responseType, "responseType");
    Objects.requireNonNull(action, "action");
    Observation observation =
        Observation.createNotStarted(OBSERVATION, observations)
            .lowCardinalityKeyValue("krizaka.idempotency.scope", request.scope())
            .start();
    try (Observation.Scope scope = observation.openScope()) {
      IdempotentOutcome<T> outcome = run(request, responseType, action);
      observation.lowCardinalityKeyValue("outcome", outcome.replayed() ? "replayed" : "executed");
      return outcome;
    } catch (InvalidIdempotencyKeyException | IdempotencyKeyReusedException rejected) {
      observation.lowCardinalityKeyValue("outcome", "rejected");
      throw rejected;
    } catch (RuntimeException | Error failure) {
      observation.lowCardinalityKeyValue("outcome", "failed");
      observation.error(failure);
      throw failure;
    } finally {
      observation.stop();
    }
  }

  private <T> IdempotentOutcome<T> run(
      IdempotentRequest request, Class<T> responseType, Supplier<T> action) {
    if (!request.keyed()) {
      return new IdempotentOutcome<>(action.get(), false);
    }
    if (!IdempotencyKeys.isValid(request.key())) {
      throw new InvalidIdempotencyKeyException();
    }
    String fingerprint = fingerprints.of(request.scope(), request.body());
    if (transactions == null) {
      return once(request, fingerprint, responseType, action);
    }
    return transactions.execute(status -> once(request, fingerprint, responseType, action));
  }

  private <T> IdempotentOutcome<T> once(
      IdempotentRequest request, String fingerprint, Class<T> responseType, Supplier<T> action) {
    Instant now = clock.instant();
    Optional<IdempotencyStore.Recorded> recorded =
        store.claim(
            request.scope(), request.subject(), request.key(), fingerprint, now, now.minus(ttl));
    if (recorded.isPresent()) {
      if (!recorded.get().fingerprint().equals(fingerprint)) {
        throw new IdempotencyKeyReusedException();
      }
      String response = recorded.get().response();
      return new IdempotentOutcome<>(
          response == null ? null : json.readValue(response, responseType), true);
    }
    T result;
    try {
      result = action.get();
    } catch (RuntimeException | Error failure) {
      if (!store.transactional()) {
        store.abandon(request.scope(), request.subject(), request.key());
      }
      throw failure;
    }
    store.complete(
        request.scope(), request.subject(), request.key(), json.writeValueAsString(result));
    return new IdempotentOutcome<>(result, false);
  }
}
