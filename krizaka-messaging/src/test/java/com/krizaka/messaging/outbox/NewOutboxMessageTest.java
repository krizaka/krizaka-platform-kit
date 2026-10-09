package com.krizaka.messaging.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class NewOutboxMessageTest {

  @Test
  void aRowWithoutAMessageIdIsRefused() {
    // The id is what consumers deduplicate on; an event row without one would be processed twice.
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new NewOutboxMessage("x", "k", null, new byte[0], Map.of()))
        .withMessageContaining("messageId");
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new NewOutboxMessage("x", "k", " ", new byte[0], Map.of()));
  }

  @Test
  void refusesAMissingDestination() {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new NewOutboxMessage("", "k", "m", new byte[0], Map.of()));
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new NewOutboxMessage("x", null, "m", new byte[0], Map.of()));
  }

  @Test
  void theBodyAndHeadersCannotBeChangedFromOutside() {
    byte[] body = "{}".getBytes(StandardCharsets.UTF_8);
    Map<String, String> headers = new HashMap<>(Map.of("kz-version", "1"));
    NewOutboxMessage message = new NewOutboxMessage("x", "k", "m", body, headers);

    body[0] = 'X';
    headers.put("kz-version", "2");
    message.body()[1] = 'Y';

    assertThat(new String(message.body(), StandardCharsets.UTF_8)).isEqualTo("{}");
    assertThat(message.headers()).containsExactly(Map.entry("kz-version", "1"));
  }

  @Test
  void equalityComparesTheBodyByContent() {
    assertThat(new NewOutboxMessage("x", "k", "m", new byte[] {1}, null))
        .isEqualTo(new NewOutboxMessage("x", "k", "m", new byte[] {1}, Map.of()))
        .hasSameHashCodeAs(new NewOutboxMessage("x", "k", "m", new byte[] {1}, Map.of()));
  }

  @Test
  void toStringNeverPrintsThePayload() {
    assertThat(
            new NewOutboxMessage("x", "k", "m", "{\"email\":\"a@b.c\"}".getBytes(), null)
                .toString())
        .doesNotContain("a@b.c")
        .contains("bytes");
  }
}
