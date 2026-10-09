package com.krizaka.web.problem;

import org.springframework.http.HttpStatus;

/**
 * The caller is known but may not do this — a business rule, not a missing token (Spring Security
 * answers that one).
 *
 * <p>Answered with {@code 403 Forbidden}; the code defaults to {@value #CODE} and may be made more
 * precise by the service.
 */
public class ForbiddenException extends DomainException {

  private static final long serialVersionUID = 1L;

  /** The default code. */
  public static final String CODE = "forbidden";

  /**
   * Creates the failure with the default code {@value #CODE}.
   *
   * @param message the detail shown to the caller
   */
  public ForbiddenException(String message) {
    this(CODE, message);
  }

  /**
   * Creates the failure with a more precise code.
   *
   * @param code the stable slug, documented with the service
   * @param message the detail shown to the caller
   */
  public ForbiddenException(String code, String message) {
    super(HttpStatus.FORBIDDEN, code, message);
  }
}
