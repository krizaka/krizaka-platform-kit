package com.krizaka.web.problem;

import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;

/**
 * The business failure a service raises — and the only exception that leaves a service as a 4xx.
 *
 * <p>It carries a status (always 4xx: a server fault is not the caller's to fix) and a stable
 * {@code code}, a lowercase slug such as {@code auction-closed}. The code is the contract: a client
 * branches on it, and {@link ProblemDetailsAdvice} turns it into the problem {@code type} ({@code
 * https://krizaka.com/problems/auction-closed}). The message becomes the {@code detail} and is
 * shown to the caller, so it must not carry anything the caller may not see.
 *
 * <p>Use the concrete subclasses ({@link NotFoundException}, {@link ConflictException}, {@link
 * ForbiddenException}, {@link ValidationException}) or extend this class for a status they do not
 * cover.
 */
public abstract class DomainException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  private static final Pattern CODE = Pattern.compile("[a-z0-9]+(-[a-z0-9]+)*");

  /** The HTTP status; transient because {@link HttpStatus} is serialisable only by name. */
  private final transient HttpStatus status;

  private final String code;

  /**
   * Creates a domain failure.
   *
   * @param status the HTTP status, a client error (4xx)
   * @param code the stable slug ({@code [a-z0-9]+(-[a-z0-9]+)*}), documented with the service
   * @param message the detail shown to the caller
   * @throws IllegalArgumentException when the status is not 4xx or the code is not a slug
   */
  protected DomainException(HttpStatus status, String code, String message) {
    super(message);
    if (status == null || !status.is4xxClientError()) {
      throw new IllegalArgumentException("a DomainException is a client error (4xx): " + status);
    }
    if (code == null || !CODE.matcher(code).matches()) {
      throw new IllegalArgumentException("a problem code is a lowercase slug: '" + code + "'");
    }
    this.status = status;
    this.code = code;
  }

  /**
   * The HTTP status the failure is answered with.
   *
   * @return a 4xx status
   */
  public HttpStatus status() {
    return status;
  }

  /**
   * The stable error code a client may branch on.
   *
   * @return a lowercase slug, such as {@code auction-closed}
   */
  public String code() {
    return code;
  }
}
