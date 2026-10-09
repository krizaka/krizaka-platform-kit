package com.krizaka.messaging.outbox;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** A test store: pending rows in a list, and a record of what the relay told it. */
final class InMemoryOutboxStore implements OutboxStore {

  final List<OutboxMessage> pending = new ArrayList<>();
  final List<UUID> published = new ArrayList<>();
  final List<UUID> failed = new ArrayList<>();
  Instant purgeCutoff;

  @Override
  public List<OutboxMessage> lockPendingBatch(int batchSize) {
    return List.copyOf(pending.subList(0, Math.min(batchSize, pending.size())));
  }

  @Override
  public void markPublished(UUID id) {
    published.add(id);
    pending.removeIf(message -> message.id().equals(id));
  }

  @Override
  public void recordFailure(UUID id, int previousAttempts) {
    failed.add(id);
  }

  @Override
  public long purgePublishedBefore(Instant cutoff) {
    purgeCutoff = cutoff;
    return 3;
  }
}
