package com.krizaka.web.problem;

import org.springframework.http.HttpStatus;

/**
 * The request is well-formed but breaks a business rule that bean validation cannot express.
 *
 * <p>Answered with {@code 422 Unprocessable Content}; the code defaults to {@value #CODE} and may
 * be made more precise by the service.
 */
public class ValidationException extends DomainException {

  private static final long serialVersionUID = 1L;

  /** The default code. */
  public static final String CODE = "validation-failed";

  /**
   * Creates the failure with the default code {@value #CODE}.
   *
   * @param message the detail shown to the caller
   */
  public ValidationException(String message) {
    this(CODE, message);
  }

  /**
   * Creates the failure with a more precise code.
   *
   * @param code the stable slug, documented with the service
   * @param message the detail shown to the caller
   */
  public ValidationException(String code, String message) {
    super(HttpStatus.UNPROCESSABLE_CONTENT, code, message);
  }
}
