package com.krizaka.messaging.dedup;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import java.time.Instant;
import java.util.Objects;

/**
 * A {@link MessageDedup} whose {@link #processOnce} is observed as {@value #OBSERVATION}: a timer
 * and a span per message, tagged with the consumer and the outcome — {@code processed}, {@code
 * duplicate} (a redelivery refused) or {@code failed} (the handler threw; the claim was released).
 *
 * <p>The duplicate rate of a consumer is what says whether the broker redelivers more than it
 * should; until now it was invisible.
 */
public final class ObservedMessageDedup implements MessageDedup {

  /** The observation of one {@link #processOnce}. */
  public static final String OBSERVATION = "krizaka.messaging.dedup";

  private final MessageDedup delegate;
  private final ObservationRegistry observations;

  private ObservedMessageDedup(MessageDedup delegate, ObservationRegistry observations) {
    this.delegate = delegate;
    this.observations = observations;
  }

  /**
   * Observes a dedup service, unless there is nothing to observe with.
   *
   * @param delegate the service
   * @param observations the registry; {@code null} or {@link ObservationRegistry#NOOP} returns the
   *     service unchanged
   * @return the observed service
   */
  public static MessageDedup of(MessageDedup delegate, ObservationRegistry observations) {
    Objects.requireNonNull(delegate, "delegate");
    if (observations == null || observations.isNoop()) {
      return delegate;
    }
    return new ObservedMessageDedup(delegate, observations);
  }

  /**
   * The observed service.
   *
   * @return the service every call is delegated to
   */
  public MessageDedup delegate() {
    return delegate;
  }

  @Override
  public boolean claim(String consumer, String messageId) {
    return delegate.claim(consumer, messageId);
  }

  @Override
  public void release(String consumer, String messageId) {
    delegate.release(consumer, messageId);
  }

  @Override
  public boolean processOnce(String consumer, String messageId, Runnable handler) {
    Observation observation =
        Observation.createNotStarted(OBSERVATION, observations)
            .lowCardinalityKeyValue("krizaka.consumer", consumer == null ? "none" : consumer)
            .start();
    try (Observation.Scope scope = observation.openScope()) {
      boolean processed = delegate.processOnce(consumer, messageId, handler);
      observation.lowCardinalityKeyValue("outcome", processed ? "processed" : "duplicate");
      return processed;
    } catch (RuntimeException | Error failure) {
      observation.lowCardinalityKeyValue("outcome", "failed");
      observation.error(failure);
      throw failure;
    } finally {
      observation.stop();
    }
  }

  @Override
  public long purgeClaimedBefore(Instant cutoff) {
    return delegate.purgeClaimedBefore(cutoff);
  }
}
