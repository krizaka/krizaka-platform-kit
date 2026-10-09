package com.krizaka.web.problem;

import org.springframework.http.HttpStatus;

/**
 * The request is valid but contradicts the resource's current state (already exists, already
 * closed, stale version).
 *
 * <p>Answered with {@code 409 Conflict}; the code defaults to {@value #CODE} and may be made more
 * precise by the service.
 */
public class ConflictException extends DomainException {

  private static final long serialVersionUID = 1L;

  /** The default code. */
  public static final String CODE = "conflict";

  /**
   * Creates the failure with the default code {@value #CODE}.
   *
   * @param message the detail shown to the caller
   */
  public ConflictException(String message) {
    this(CODE, message);
  }

  /**
   * Creates the failure with a more precise code.
   *
   * @param code the stable slug, documented with the service
   * @param message the detail shown to the caller
   */
  public ConflictException(String code, String message) {
    super(HttpStatus.CONFLICT, code, message);
  }
}
