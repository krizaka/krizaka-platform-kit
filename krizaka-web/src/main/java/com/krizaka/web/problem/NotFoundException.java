package com.krizaka.web.problem;

import org.springframework.http.HttpStatus;

/**
 * The resource the caller named does not exist — or the caller may not know that it does.
 *
 * <p>Answered with {@code 404 Not Found}; the code defaults to {@value #CODE} and may be made more
 * precise by the service.
 */
public class NotFoundException extends DomainException {

  private static final long serialVersionUID = 1L;

  /** The default code. */
  public static final String CODE = "not-found";

  /**
   * Creates the failure with the default code {@value #CODE}.
   *
   * @param message the detail shown to the caller
   */
  public NotFoundException(String message) {
    this(CODE, message);
  }

  /**
   * Creates the failure with a more precise code.
   *
   * @param code the stable slug, documented with the service
   * @param message the detail shown to the caller
   */
  public NotFoundException(String code, String message) {
    super(HttpStatus.NOT_FOUND, code, message);
  }
}
