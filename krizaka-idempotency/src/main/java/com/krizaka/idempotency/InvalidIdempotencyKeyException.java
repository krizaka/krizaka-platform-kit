package com.krizaka.idempotency;

import com.krizaka.web.problem.DomainException;
import java.io.Serial;
import org.springframework.http.HttpStatus;

/** A malformed key: {@code 400}, code {@value #CODE}. */
public class InvalidIdempotencyKeyException extends DomainException {

  @Serial private static final long serialVersionUID = 1L;

  /** The problem code. */
  public static final String CODE = "invalid-idempotency-key";

  /** Creates the exception. */
  public InvalidIdempotencyKeyException() {
    super(HttpStatus.BAD_REQUEST, CODE, "An Idempotency-Key is 1 to 255 visible ASCII characters.");
  }
}
