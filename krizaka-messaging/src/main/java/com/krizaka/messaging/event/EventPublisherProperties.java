package com.krizaka.messaging.event;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Who publishes ({@code krizaka.messaging}).
 *
 * <pre>{@code
 * krizaka:
 *   messaging:
 *     producer: krizaka-users   # stamped on every event as kz-producer
 * }</pre>
 *
 * @param producer Name of this service, stamped on every event it publishes (kz-producer). Required
 *     as soon as an EventPublisher is injected; never guessed from the application name.
 */
@ConfigurationProperties(prefix = "krizaka.messaging")
public record EventPublisherProperties(String producer) {}
