package com.krizaka.idempotency;

import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;

/**
 * The result of an idempotent action.
 *
 * @param response what the action returned — now, or the first time this key ran it
 * @param replayed {@code true} when the key had already run the action and this is its stored
 *     response
 * @param <T> the response type
 */
public record IdempotentOutcome<T>(T response, boolean replayed) {

  /**
   * The outcome as an HTTP answer, flagged {@value IdempotencyKeys#REPLAYED_HEADER}{@code : true}
   * when it is a replay — what Stripe and the IETF draft call it.
   *
   * @param status the status of the original answer (a replay answers the same)
   * @return the response entity
   */
  public ResponseEntity<T> toResponse(HttpStatusCode status) {
    ResponseEntity.BodyBuilder builder = ResponseEntity.status(status);
    if (replayed) {
      builder.header(IdempotencyKeys.REPLAYED_HEADER, "true");
    }
    return builder.body(response);
  }
}
