package com.krizaka.messaging.outbox;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import org.junit.jupiter.api.Test;

class OutboxStoreTest {

  @Test
  void aStoreWrittenBeforeAppendRefusesItWithTheReason() {
    OutboxStore legacy = new InMemoryOutboxStore();

    assertThatThrownBy(
            () -> legacy.append(new NewOutboxMessage("x", "k", "m", new byte[0], Map.of())))
        .isInstanceOf(UnsupportedOperationException.class)
        .hasMessageContaining(InMemoryOutboxStore.class.getName())
        .hasMessageContaining("append(NewOutboxMessage)")
        .hasMessageContaining("EventPublisher");
  }
}
