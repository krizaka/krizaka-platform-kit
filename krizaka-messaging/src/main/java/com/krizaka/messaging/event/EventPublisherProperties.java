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
 * @param producer the name of this service, stamped on every event it publishes; required as soon
 *     as an {@link EventPublisher} is injected
 */
@ConfigurationProperties(prefix = "krizaka.messaging")
public record EventPublisherProperties(String producer) {}
