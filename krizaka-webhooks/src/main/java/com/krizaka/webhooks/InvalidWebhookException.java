package com.krizaka.webhooks;

import com.krizaka.web.problem.DomainException;
import java.io.Serial;
import org.springframework.http.HttpStatus;

/**
 * A webhook that cannot be trusted: {@code 401}, code {@value #CODE}. The detail says which check
 * failed (headers, timestamp, signature), never a secret or an expected signature.
 */
public class InvalidWebhookException extends DomainException {

  @Serial private static final long serialVersionUID = 1L;

  /** The problem code. */
  public static final String CODE = "invalid-webhook";

  /**
   * Creates the exception.
   *
   * @param reason which check failed, for the sender's logs
   */
  public InvalidWebhookException(String reason) {
    super(HttpStatus.UNAUTHORIZED, CODE, reason);
  }
}
