package com.krizaka.example.idempotency;

/**
 * An order to create.
 *
 * @param sku what to order
 * @param quantity how many
 */
public record NewOrder(String sku, int quantity) {}
