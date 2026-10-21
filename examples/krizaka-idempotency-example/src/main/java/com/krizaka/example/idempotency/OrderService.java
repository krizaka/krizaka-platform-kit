package com.krizaka.example.idempotency;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/** Creates orders: the work a retry must never repeat. */
@Service
public class OrderService {

  private final JdbcTemplate jdbc;

  OrderService(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  /**
   * Creates an order.
   *
   * @param customer who orders
   * @param order what
   * @return the created order
   */
  public Order create(String customer, NewOrder order) {
    Order created = new Order(UUID.randomUUID(), order.sku(), order.quantity(), Instant.now());
    jdbc.update(
        "INSERT INTO orders (id, customer, sku, quantity, created_at) VALUES (?, ?, ?, ?, ?)",
        created.id(),
        customer,
        created.sku(),
        created.quantity(),
        Timestamp.from(created.createdAt()));
    return created;
  }
}
