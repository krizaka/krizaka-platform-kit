package com.krizaka.ratelimit;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Objects;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Applies the limit before a controller runs. A refusal is a {@link RateLimitExceededException}, so
 * the Problem Details advice of {@code krizaka-web} answers it: {@code 429}, code {@code
 * rate-limit-exceeded}, the request id — and the {@code Retry-After} header set here.
 *
 * <p>Every answer carries {@code RateLimit-Limit} and {@code RateLimit-Remaining}. Observed as
 * {@value #OBSERVATION}, tagged {@code outcome}: {@code allowed}, {@code limited} (refused) or
 * {@code observed} (over the limit in observe mode, not refused).
 */
public final class RateLimitInterceptor implements HandlerInterceptor {

  /** The observation of one request. */
  public static final String OBSERVATION = "krizaka.ratelimit";

  /** The header carrying the limit applied. */
  public static final String LIMIT_HEADER = "RateLimit-Limit";

  /** The header carrying the calls left. */
  public static final String REMAINING_HEADER = "RateLimit-Remaining";

  private final RateLimiter limiter;
  private final RateLimitKeyResolver keys;
  private final ObservationRegistry observations;

  /**
   * The interceptor.
   *
   * @param limiter the limiter
   * @param keys who a request is
   * @param observations where requests are observed
   */
  public RateLimitInterceptor(
      RateLimiter limiter, RateLimitKeyResolver keys, ObservationRegistry observations) {
    this.limiter = Objects.requireNonNull(limiter, "limiter");
    this.keys = Objects.requireNonNull(keys, "keys");
    this.observations = Objects.requireNonNull(observations, "observations");
  }

  @Override
  public boolean preHandle(
      HttpServletRequest request, HttpServletResponse response, Object handler) {
    if (!limiter.active()) {
      return true;
    }
    RateLimitKey key = keys.resolve(request);
    if (key == null) {
      return true;
    }
    Observation observation = Observation.createNotStarted(OBSERVATION, observations).start();
    try {
      RateLimitDecision decision = limiter.tryAcquire(key);
      response.setHeader(LIMIT_HEADER, Integer.toString(decision.limit()));
      response.setHeader(REMAINING_HEADER, Integer.toString(decision.remaining()));
      if (!decision.allowed()) {
        observation.lowCardinalityKeyValue("outcome", "limited");
        response.setHeader("Retry-After", Long.toString(decision.retryAfterSeconds()));
        throw new RateLimitExceededException(decision.limit(), decision.retryAfterSeconds());
      }
      observation.lowCardinalityKeyValue("outcome", decision.overLimit() ? "observed" : "allowed");
      return true;
    } finally {
      observation.stop();
    }
  }
}
