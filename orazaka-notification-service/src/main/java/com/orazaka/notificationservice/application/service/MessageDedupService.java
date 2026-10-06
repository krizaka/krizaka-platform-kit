package com.orazaka.notificationservice.application.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;

/**
 * Consumer-side idempotency by AMQP {@code messageId} (AGENTS.md §6): a message is claimed before
 * it is processed and released when processing fails, so a redelivery after a failure is processed
 * again and a duplicate delivery is processed once.
 *
 * <p>The claim is atomic ({@link ConcurrentHashMap#putIfAbsent}) and held in memory for {@link
 * #RETENTION}: this service is stateless by design, so it guards against the broker's redeliveries
 * and a producer's double publish, not against a duplicate that arrives after a restart. A
 * deployment that needs the stronger guarantee replaces this class with a claim on a unique row,
 * like the automation service's.
 */
@Service
public class MessageDedupService {

  static final Duration RETENTION = Duration.ofHours(24);
  static final int MAX_CLAIMS = 100_000;

  private final Map<String, Instant> claims = new ConcurrentHashMap<>();
  private final Clock clock;

  public MessageDedupService(Clock clock) {
    this.clock = clock;
  }

  /**
   * Claims a message for processing, atomically.
   *
   * @param consumer who is processing
   * @param messageId the broker's message id; blank is claimable, because a producer that sent no
   *     id asked for no deduplication and must not be silently dropped
   * @return {@code true} when this caller may process it
   */
  public boolean claim(String consumer, String messageId) {
    if (messageId == null || messageId.isBlank()) {
      return true;
    }
    Instant now = clock.instant();
    if (claims.size() >= MAX_CLAIMS) {
      Instant horizon = now.minus(RETENTION);
      claims.values().removeIf(claimedAt -> claimedAt.isBefore(horizon));
    }
    return claims.putIfAbsent(consumer + ":" + messageId, now) == null;
  }

  /**
   * Gives a claim back after processing failed, so the redelivery is processed.
   *
   * @param consumer who was processing
   * @param messageId the message to make claimable again
   */
  public void release(String consumer, String messageId) {
    if (messageId == null || messageId.isBlank()) {
      return;
    }
    claims.remove(consumer + ":" + messageId);
  }
}
