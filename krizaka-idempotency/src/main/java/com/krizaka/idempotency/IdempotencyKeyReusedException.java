package com.krizaka.idempotency;

import com.krizaka.web.problem.DomainException;
import java.io.Serial;
import org.springframework.http.HttpStatus;

/**
 * A key reused for a different request: {@code 422}, code {@value #CODE}. The client generated one
 * key for two operations; replaying the first would answer a question it did not ask.
 */
public class IdempotencyKeyReusedException extends DomainException {

  @Serial private static final long serialVersionUID = 1L;

  /** The problem code. */
  public static final String CODE = "idempotency-key-reused";

  /** Creates the exception. */
  public IdempotencyKeyReusedException() {
    super(
        HttpStatus.UNPROCESSABLE_CONTENT,
        CODE,
        "This Idempotency-Key was already used for a different request.");
  }
}
