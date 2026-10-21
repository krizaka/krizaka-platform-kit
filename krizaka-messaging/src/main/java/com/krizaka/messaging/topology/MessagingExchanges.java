package com.krizaka.messaging.topology;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * The exchanges a Krizaka building block publishes to and dead-letters through ({@code
 * krizaka.messaging.exchanges}).
 *
 * <p>A building block never hard-codes the bus it runs on: the application that hosts it owns its
 * exchanges, and names them here. Without configuration the block uses its own defaults, which is
 * what a standalone deployment wants.
 *
 * <pre>{@code
 * krizaka:
 *   messaging:
 *     exchanges:
 *       events: platform.events      # topic exchange of domain events
 *       dead-letter: platform.dlx    # direct exchange every queue dead-letters to
 * }</pre>
 *
 * @param events Topic exchange domain events are published to.
 * @param deadLetter Direct exchange failed messages are dead-lettered to; must differ from the
 *     events exchange.
 */
@ConfigurationProperties(prefix = "krizaka.messaging.exchanges")
public record MessagingExchanges(
    @DefaultValue(MessagingExchanges.DEFAULT_EVENTS) String events,
    @DefaultValue(MessagingExchanges.DEFAULT_DEAD_LETTER) String deadLetter) {

  /** The default events exchange. */
  public static final String DEFAULT_EVENTS = "krizaka.events";

  /** The default dead-letter exchange. */
  public static final String DEFAULT_DEAD_LETTER = "krizaka.dlx";

  /** Applies the defaults and refuses two exchanges with the same name. */
  public MessagingExchanges {
    events = events == null || events.isBlank() ? DEFAULT_EVENTS : events;
    deadLetter = deadLetter == null || deadLetter.isBlank() ? DEFAULT_DEAD_LETTER : deadLetter;
    if (events.equals(deadLetter)) {
      throw new IllegalArgumentException(
          "krizaka.messaging.exchanges: the events and dead-letter exchanges must differ");
    }
  }

  /**
   * The defaults, for code that runs without a Spring context (tests, tools).
   *
   * @return {@value #DEFAULT_EVENTS} and {@value #DEFAULT_DEAD_LETTER}
   */
  public static MessagingExchanges defaults() {
    return new MessagingExchanges(null, null);
  }
}
