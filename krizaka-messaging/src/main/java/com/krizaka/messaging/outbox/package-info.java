/**
 * Transactional outbox: the application implements {@link com.krizaka.messaging.outbox.OutboxStore}
 * over its own table, and {@link com.krizaka.messaging.outbox.OutboxRelay} publishes the rows to
 * RabbitMQ, claimed and exactly once per success.
 */
package com.krizaka.messaging.outbox;
