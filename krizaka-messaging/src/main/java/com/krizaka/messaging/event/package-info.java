/**
 * Domain events: {@link com.krizaka.messaging.event.EventPublisher} writes an event to the
 * context's outbox in the business transaction, with its envelope in AMQP headers ({@link
 * com.krizaka.messaging.event.EventHeaders}) and the bare event as the body.
 */
package com.krizaka.messaging.event;
