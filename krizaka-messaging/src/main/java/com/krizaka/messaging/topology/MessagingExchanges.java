package com.krizaka.messaging.topology;

import org.springframework.boot.context.properties.ConfigurationProperties;

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
 * @param events the topic exchange domain events are published to; default {@value #DEFAULT_EVENTS}
 * @param deadLetter the direct exchange failed messages are dead-lettered to; default {@value
 *     #DEFAULT_DEAD_LETTER}
 */
@ConfigurationProperties(prefix = "krizaka.messaging.exchanges")
public record MessagingExchanges(String events, String deadLetter) {

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
