package com.krizaka.messaging;

import io.micrometer.common.KeyValue;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationHandler;
import io.micrometer.observation.ObservationRegistry;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

/** An {@link ObservationRegistry} that keeps every stopped observation, for assertions. */
public final class RecordedObservations implements ObservationHandler<Observation.Context> {

  /** One stopped observation. */
  public record Stopped(
      String name, Map<String, String> low, Map<String, String> high, Throwable error) {}

  private final List<Stopped> stopped = new CopyOnWriteArrayList<>();
  private final ObservationRegistry registry = ObservationRegistry.create();

  /** A registry whose observations are recorded. */
  public RecordedObservations() {
    registry.observationConfig().observationHandler(this);
  }

  /**
   * The registry to observe with.
   *
   * @return the registry
   */
  public ObservationRegistry registry() {
    return registry;
  }

  /**
   * The stopped observations with this name.
   *
   * @param name the observation's name
   * @return them, in the order they stopped
   */
  public List<Stopped> named(String name) {
    return stopped.stream().filter(s -> s.name().equals(name)).toList();
  }

  @Override
  public void onStop(Observation.Context context) {
    stopped.add(
        new Stopped(
            context.getName(),
            toMap(context.getLowCardinalityKeyValues()),
            toMap(context.getHighCardinalityKeyValues()),
            context.getError()));
  }

  @Override
  public boolean supportsContext(Observation.Context context) {
    return true;
  }

  private static Map<String, String> toMap(Iterable<KeyValue> keyValues) {
    return java.util.stream.StreamSupport.stream(keyValues.spliterator(), false)
        .collect(Collectors.toMap(KeyValue::getKey, KeyValue::getValue, (a, b) -> b));
  }
}
