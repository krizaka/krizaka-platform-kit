package com.krizaka.idempotency;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Keys in this instance's memory: tests, demos, a single instance that accepts forgetting its keys
 * on restart. A second claim of a key in progress waits for the first to complete or be abandoned,
 * like the JDBC store.
 */
public final class InMemoryIdempotencyStore implements IdempotencyStore {

  private record Id(String scope, String subject, String key) {}

  private record Entry(String fingerprint, String response, Instant createdAt, boolean done) {}

  private final Map<Id, Entry> entries = new HashMap<>();

  /** An empty store. */
  public InMemoryIdempotencyStore() {}

  @Override
  public synchronized Optional<Recorded> claim(
      String scope,
      String subject,
      String key,
      String fingerprint,
      Instant now,
      Instant expiredBefore) {
    Id id = new Id(scope, subject, key);
    while (true) {
      Entry entry = entries.get(id);
      if (entry == null || (entry.done() && entry.createdAt().isBefore(expiredBefore))) {
        entries.put(id, new Entry(fingerprint, null, now, false));
        return Optional.empty();
      }
      if (entry.done()) {
        return Optional.of(new Recorded(entry.fingerprint(), entry.response()));
      }
      try {
        wait();
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new IllegalStateException("interrupted while another request held the key", e);
      }
    }
  }

  @Override
  public synchronized void complete(String scope, String subject, String key, String response) {
    Id id = new Id(scope, subject, key);
    Entry entry = entries.get(id);
    if (entry != null) {
      entries.put(id, new Entry(entry.fingerprint(), response, entry.createdAt(), true));
    }
    notifyAll();
  }

  @Override
  public synchronized void abandon(String scope, String subject, String key) {
    entries.remove(new Id(scope, subject, key));
    notifyAll();
  }

  @Override
  public synchronized long purgeBefore(Instant cutoff) {
    List<Id> old =
        entries.entrySet().stream()
            .filter(e -> e.getValue().done() && e.getValue().createdAt().isBefore(cutoff))
            .map(Map.Entry::getKey)
            .toList();
    old.forEach(entries::remove);
    return old.size();
  }
}
