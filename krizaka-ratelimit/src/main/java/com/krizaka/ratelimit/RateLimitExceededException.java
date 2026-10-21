package com.krizaka.ratelimit;

import com.krizaka.web.problem.DomainException;
import java.io.Serial;
import org.springframework.http.HttpStatus;

/**
 * A key that spent its requests per minute: {@code 429}, code {@value #CODE}. The {@code
 * Retry-After} header says how long to wait.
 */
public class RateLimitExceededException extends DomainException {

  @Serial private static final long serialVersionUID = 1L;

  /** The problem code. */
  public static final String CODE = "rate-limit-exceeded";

  private final long retryAfterSeconds;

  /**
   * Creates the exception.
   *
   * @param limit the requests per minute that were spent
   * @param retryAfterSeconds when a call can succeed
   */
  public RateLimitExceededException(int limit, long retryAfterSeconds) {
    super(
        HttpStatus.TOO_MANY_REQUESTS,
        CODE,
        "Too many requests: at most %d a minute. Retry in %d s."
            .formatted(limit, retryAfterSeconds));
    this.retryAfterSeconds = retryAfterSeconds;
  }

  /**
   * When a call can succeed.
   *
   * @return seconds, at least 1
   */
  public long retryAfterSeconds() {
    return retryAfterSeconds;
  }
}
