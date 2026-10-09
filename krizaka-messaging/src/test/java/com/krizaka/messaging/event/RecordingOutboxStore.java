package com.krizaka.messaging.event;

import com.krizaka.messaging.outbox.NewOutboxMessage;
import com.krizaka.messaging.outbox.OutboxMessage;
import com.krizaka.messaging.outbox.OutboxStore;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** A test store that records what was appended. */
final class RecordingOutboxStore implements OutboxStore {

  final List<NewOutboxMessage> appended = new ArrayList<>();

  @Override
  public void append(NewOutboxMessage message) {
    appended.add(message);
  }

  @Override
  public List<OutboxMessage> lockPendingBatch(int batchSize) {
    return List.of();
  }

  @Override
  public void markPublished(UUID id) {}

  @Override
  public void recordFailure(UUID id, int previousAttempts) {}

  @Override
  public long purgePublishedBefore(Instant cutoff) {
    return 0;
  }
}
