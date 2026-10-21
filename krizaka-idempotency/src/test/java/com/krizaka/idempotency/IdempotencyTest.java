package com.krizaka.idempotency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationHandler;
import io.micrometer.observation.ObservationRegistry;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import tools.jackson.databind.json.JsonMapper;

class IdempotencyTest {

  record Order(String id, int quantity) {}

  private final MutableClock clock = new MutableClock(Instant.parse("2026-10-20T12:00:00Z"));
  private final List<String> outcomes = new CopyOnWriteArrayList<>();
  private final Idempotency idempotency =
      new Idempotency(
          new InMemoryIdempotencyStore(),
          JsonMapper.shared(),
          null,
          Duration.ofHours(24),
          clock,
          recording());
  private final AtomicInteger runs = new AtomicInteger();

  private Order create() {
    return new Order("o-" + runs.incrementAndGet(), 2);
  }

  private IdempotentRequest request(String key, Object body) {
    return new IdempotentRequest("orders.create", "user-1", key, body);
  }

  @Test
  void theFirstRequestRunsAndTheRetryReplaysItsResponse() {
    IdempotentOutcome<Order> first =
        idempotency.execute(request("k-1", Map.of("sku", "tea")), Order.class, this::create);
    IdempotentOutcome<Order> retry =
        idempotency.execute(request("k-1", Map.of("sku", "tea")), Order.class, this::create);

    assertThat(first.replayed()).isFalse();
    assertThat(retry.replayed()).isTrue();
    assertThat(retry.response()).isEqualTo(first.response());
    assertThat(runs).hasValue(1);
    assertThat(outcomes).containsExactly("executed", "replayed");
  }

  @Test
  void theOrderOfTheFieldsIsNotADifference() {
    idempotency.execute(
        request("k-2", new java.util.LinkedHashMap<>(Map.of("a", 1))), Order.class, this::create);
    java.util.LinkedHashMap<String, Object> one = new java.util.LinkedHashMap<>();
    one.put("sku", "tea");
    one.put("quantity", 2);
    java.util.LinkedHashMap<String, Object> other = new java.util.LinkedHashMap<>();
    other.put("quantity", 2);
    other.put("sku", "tea");

    idempotency.execute(request("k-3", one), Order.class, this::create);

    assertThat(idempotency.execute(request("k-3", other), Order.class, this::create).replayed())
        .isTrue();
  }

  @Test
  void theSameKeyForAnotherRequestIsRefused() {
    idempotency.execute(request("k-4", Map.of("sku", "tea")), Order.class, this::create);

    assertThatThrownBy(
            () ->
                idempotency.execute(
                    request("k-4", Map.of("sku", "coffee")), Order.class, this::create))
        .isInstanceOf(IdempotencyKeyReusedException.class)
        .satisfies(
            e -> {
              IdempotencyKeyReusedException reused = (IdempotencyKeyReusedException) e;
              assertThat(reused.status()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
              assertThat(reused.code()).isEqualTo("idempotency-key-reused");
            });
    assertThat(runs).hasValue(1);
    assertThat(outcomes).containsExactly("executed", "rejected");
  }

  @Test
  void aKeyOfOneSubjectNeverReplaysAnothers() {
    idempotency.execute(request("k-5", null), Order.class, this::create);

    IdempotentOutcome<Order> other =
        idempotency.execute(
            new IdempotentRequest("orders.create", "user-2", "k-5", null),
            Order.class,
            this::create);

    assertThat(other.replayed()).isFalse();
    assertThat(runs).hasValue(2);
  }

  @Test
  void withoutAKeyTheActionAlwaysRuns() {
    idempotency.execute(request(null, null), Order.class, this::create);
    idempotency.execute(request(" ", null), Order.class, this::create);

    assertThat(runs).hasValue(2);
  }

  @Test
  void aMalformedKeyIsA400() {
    assertThatThrownBy(
            () -> idempotency.execute(request("has space", null), Order.class, this::create))
        .isInstanceOf(InvalidIdempotencyKeyException.class)
        .satisfies(
            e ->
                assertThat(((InvalidIdempotencyKeyException) e).status())
                    .isEqualTo(HttpStatus.BAD_REQUEST));
    assertThatThrownBy(
            () -> idempotency.execute(request("k".repeat(256), null), Order.class, this::create))
        .isInstanceOf(InvalidIdempotencyKeyException.class);
    assertThat(runs).hasValue(0);
  }

  @Test
  void aFailedActionRecordsNothingAndTheRetryRunsForReal() {
    assertThatThrownBy(
            () ->
                idempotency.execute(
                    request("k-6", null),
                    Order.class,
                    () -> {
                      throw new IllegalStateException("payment provider down");
                    }))
        .hasMessage("payment provider down");

    IdempotentOutcome<Order> retry =
        idempotency.execute(request("k-6", null), Order.class, this::create);

    assertThat(retry.replayed()).isFalse();
    assertThat(runs).hasValue(1);
    assertThat(outcomes).containsExactly("failed", "executed");
  }

  @Test
  void anExpiredKeyRunsAgain() {
    idempotency.execute(request("k-7", null), Order.class, this::create);
    clock.advance(Duration.ofHours(25));

    assertThat(idempotency.execute(request("k-7", null), Order.class, this::create).replayed())
        .isFalse();
    assertThat(runs).hasValue(2);
  }

  @Test
  void twoConcurrentRequestsWithOneKeyRunTheActionOnce() throws Exception {
    CountDownLatch inside = new CountDownLatch(1);
    CountDownLatch release = new CountDownLatch(1);
    ExecutorService pool = Executors.newFixedThreadPool(2);
    try {
      Future<IdempotentOutcome<Order>> first =
          pool.submit(
              () ->
                  idempotency.execute(
                      request("k-8", null),
                      Order.class,
                      () -> {
                        inside.countDown();
                        await(release);
                        return create();
                      }));
      assertThat(inside.await(5, TimeUnit.SECONDS)).isTrue();
      Future<IdempotentOutcome<Order>> second =
          pool.submit(() -> idempotency.execute(request("k-8", null), Order.class, this::create));
      Thread.sleep(200);
      assertThat(second.isDone()).as("the second request waits for the first").isFalse();
      release.countDown();

      assertThat(first.get(5, TimeUnit.SECONDS).replayed()).isFalse();
      assertThat(second.get(5, TimeUnit.SECONDS).replayed()).isTrue();
      assertThat(runs).hasValue(1);
    } finally {
      pool.shutdownNow();
    }
  }

  @Test
  void theOutcomeIsAnHttpAnswerFlaggedWhenReplayed() {
    idempotency.execute(request("k-9", null), Order.class, this::create);

    ResponseEntity<Order> replay =
        idempotency
            .execute(request("k-9", null), Order.class, this::create)
            .toResponse(HttpStatus.CREATED);

    assertThat(replay.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    assertThat(replay.getHeaders().getFirst(IdempotencyKeys.REPLAYED_HEADER)).isEqualTo("true");
    assertThat(
            new IdempotentOutcome<>("x", false)
                .toResponse(HttpStatus.OK)
                .getHeaders()
                .getFirst(IdempotencyKeys.REPLAYED_HEADER))
        .isNull();
  }

  @Test
  void theScopeAndSubjectAreRequired() {
    assertThatThrownBy(() -> new IdempotentRequest(" ", "u", "k", null))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new IdempotentRequest("s", null, "k", null))
        .isInstanceOf(NullPointerException.class);
  }

  private static void await(CountDownLatch latch) {
    try {
      latch.await(5, TimeUnit.SECONDS);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }

  private ObservationRegistry recording() {
    ObservationRegistry registry = ObservationRegistry.create();
    registry
        .observationConfig()
        .observationHandler(
            new ObservationHandler<>() {
              @Override
              public void onStop(Observation.Context context) {
                var outcome = context.getLowCardinalityKeyValue("outcome");
                outcomes.add(outcome == null ? "none" : outcome.getValue());
              }

              @Override
              public boolean supportsContext(Observation.Context context) {
                return Idempotency.OBSERVATION.equals(context.getName());
              }
            });
    return registry;
  }

  static final class MutableClock extends Clock {
    private Instant now;

    MutableClock(Instant now) {
      this.now = now;
    }

    void advance(Duration duration) {
      now = now.plus(duration);
    }

    @Override
    public ZoneId getZone() {
      return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
      return this;
    }

    @Override
    public Instant instant() {
      return now;
    }
  }
}
