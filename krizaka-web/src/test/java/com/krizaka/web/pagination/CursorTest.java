package com.krizaka.web.pagination;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.junit.jupiter.api.Test;

class CursorTest {

  private static String token(String payload) {
    return Base64.getUrlEncoder()
        .withoutPadding()
        .encodeToString(payload.getBytes(StandardCharsets.UTF_8));
  }

  @Test
  void roundTrips() {
    Cursor cursor = Cursor.startingAfter(1_234_567L, 20);

    Cursor decoded = Cursor.decode(cursor.encode());

    assertThat(decoded).isEqualTo(cursor);
    assertThat(decoded.afterAsLong()).isEqualTo(1_234_567L);
  }

  @Test
  void roundTripsAnyKeyAndTheFirstPage() {
    Cursor uuid = new Cursor("0192f1a4-7b1c-7c3e-9a51-2d1f0e9b7a10|x", 100);
    assertThat(Cursor.decode(uuid.encode())).isEqualTo(uuid);

    Cursor first = Cursor.first(5);
    assertThat(Cursor.decode(first.encode())).isEqualTo(first);
    assertThat(first.afterAsLong()).isNull();
  }

  @Test
  void isOpaqueAndUrlSafe() {
    assertThat(Cursor.startingAfter(42, 20).encode()).matches("[A-Za-z0-9_-]+");
  }

  @Test
  void aRequestWithoutCursorStartsAtTheFirstPage() {
    assertThat(Cursor.fromRequest(null, 20)).isEqualTo(Cursor.first(20));
    assertThat(Cursor.fromRequest(" ", 20)).isEqualTo(Cursor.first(20));
    String next = Cursor.startingAfter(7, 10).encode();
    assertThat(Cursor.fromRequest(next, 20)).isEqualTo(Cursor.startingAfter(7, 10));
  }

  @Test
  void capsThePageSize() {
    assertThatThrownBy(() -> Cursor.first(0)).isInstanceOf(InvalidCursorException.class);
    assertThatThrownBy(() -> Cursor.first(Cursor.MAX_LIMIT + 1))
        .isInstanceOf(InvalidCursorException.class);
  }

  @Test
  void rejectsAForgedCursor() {
    String valid = Cursor.startingAfter(42, 20).encode();
    for (String forged :
        new String[] {
          "not base64!",
          valid + "=",
          valid.substring(0, valid.length() - 1),
          token("2|20|42"),
          token("1|1000|42"),
          token("1|-1|42"),
          token("1|twenty|42"),
          token("1|20|"),
          token("1"),
          token("1|20|" + "x".repeat(Cursor.MAX_KEY_LENGTH + 1)),
          token("1|020|42")
        }) {
      assertThatThrownBy(() -> Cursor.decode(forged))
          .as(forged)
          .isInstanceOf(InvalidCursorException.class)
          .extracting("code")
          .isEqualTo("invalid-cursor");
    }
  }

  @Test
  void rejectsANegativeOrNonNumericKeyWhereANumberIsExpected() {
    assertThatThrownBy(() -> Cursor.startingAfter(-1, 20))
        .isInstanceOf(InvalidCursorException.class);
    assertThatThrownBy(() -> new Cursor("abc", 20).afterAsLong())
        .isInstanceOf(InvalidCursorException.class);
    assertThatThrownBy(() -> new Cursor("-5", 20).afterAsLong())
        .isInstanceOf(InvalidCursorException.class);
  }
}
