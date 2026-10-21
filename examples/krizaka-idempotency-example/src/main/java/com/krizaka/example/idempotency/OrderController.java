package com.krizaka.example.idempotency;

import com.krizaka.idempotency.Idempotency;
import com.krizaka.idempotency.IdempotencyKeys;
import com.krizaka.idempotency.IdempotentRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code POST /api/v1/orders}, safe to retry with an {@code Idempotency-Key}. The customer comes
 * from a header to keep the example short; a real service takes the authenticated principal.
 */
@RestController
class OrderController {

  private final Idempotency idempotency;
  private final OrderService orders;

  OrderController(Idempotency idempotency, OrderService orders) {
    this.idempotency = idempotency;
    this.orders = orders;
  }

  @PostMapping("/api/v1/orders")
  ResponseEntity<Order> create(
      @RequestBody NewOrder order,
      @RequestHeader("X-Customer") String customer,
      @RequestHeader(value = IdempotencyKeys.KEY_HEADER, required = false) String key) {
    return idempotency
        .execute(
            new IdempotentRequest("orders.create", customer, key, order),
            Order.class,
            () -> orders.create(customer, order))
        .toResponse(HttpStatus.CREATED);
  }
}
