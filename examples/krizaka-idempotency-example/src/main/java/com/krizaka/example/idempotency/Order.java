package com.krizaka.example.idempotency;

import java.time.Instant;
import java.util.UUID;

/**
 * A created order.
 *
 * @param id its id
 * @param sku what was ordered
 * @param quantity how many
 * @param createdAt when
 */
public record Order(UUID id, String sku, int quantity, Instant createdAt) {}
