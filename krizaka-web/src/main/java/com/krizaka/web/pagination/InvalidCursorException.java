package com.krizaka.web.pagination;

import com.krizaka.web.problem.DomainException;
import org.springframework.http.HttpStatus;

/**
 * A cursor the service did not produce, or a page size out of bounds: {@code 400 invalid-cursor}.
 */
public class InvalidCursorException extends DomainException {

  private static final long serialVersionUID = 1L;

  /** The problem code. */
  public static final String CODE = "invalid-cursor";

  /**
   * Creates the failure.
   *
   * @param message what is wrong with the cursor, shown to the caller
   */
  public InvalidCursorException(String message) {
    super(HttpStatus.BAD_REQUEST, CODE, message);
  }
}
