package com.krizaka.messaging.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class OutboxMessageTest {

  private static final UUID ID = UUID.randomUUID();

  @Test
  void theBodyCannotBeChangedFromOutside() {
    byte[] body = "{}".getBytes(StandardCharsets.UTF_8);
    OutboxMessage message = new OutboxMessage(ID, "x", "k", "m", body, 0);

    body[0] = 'X';
    message.body()[1] = 'Y';

    assertThat(new String(message.body(), StandardCharsets.UTF_8)).isEqualTo("{}");
  }

  @Test
  void equalityComparesTheBodyByContent() {
    assertThat(new OutboxMessage(ID, "x", "k", "m", new byte[] {1}, 0))
        .isEqualTo(new OutboxMessage(ID, "x", "k", "m", new byte[] {1}, 0))
        .hasSameHashCodeAs(new OutboxMessage(ID, "x", "k", "m", new byte[] {1}, 0));
  }

  @Test
  void refusesAnIncompleteRow() {
    assertThatNullPointerException()
        .isThrownBy(() -> new OutboxMessage(ID, null, "k", "m", new byte[0], 0));
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new OutboxMessage(ID, "x", "k", "m", new byte[0], -1));
  }

  @Test
  void toStringNeverPrintsThePayload() {
    String text =
        new OutboxMessage(ID, "x", "k", "m", "{\"email\":\"a@b.c\"}".getBytes(), 0).toString();
    assertThat(text).doesNotContain("a@b.c").contains("bytes");
  }
}
