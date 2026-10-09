package com.krizaka.messaging.topology;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

/** Binds {@link MessagingExchanges} for every application that has krizaka-messaging. */
@AutoConfiguration
@EnableConfigurationProperties(MessagingExchanges.class)
public class MessagingExchangesAutoConfiguration {}
