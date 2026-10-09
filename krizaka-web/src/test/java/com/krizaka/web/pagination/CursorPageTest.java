package com.krizaka.web.pagination;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class CursorPageTest {

  record Item(long id) {}

  @Test
  void theExtraRowProvesANextPageAndIsNotServed() {
    Cursor cursor = Cursor.first(2);

    CursorPage<Item> page =
        CursorPage.of(List.of(new Item(1), new Item(2), new Item(3)), cursor, Item::id);

    assertThat(page.items()).containsExactly(new Item(1), new Item(2));
    assertThat(page.hasNext()).isTrue();
    assertThat(Cursor.decode(page.nextCursor())).isEqualTo(Cursor.startingAfter(2, 2));
  }

  @Test
  void theLastPageHasNoNextCursor() {
    CursorPage<Item> page =
        CursorPage.of(List.of(new Item(1), new Item(2)), Cursor.first(2), Item::id);

    assertThat(page.items()).hasSize(2);
    assertThat(page.hasNext()).isFalse();
    assertThat(page.nextCursor()).isNull();
  }

  @Test
  void anEmptyPage() {
    assertThat(CursorPage.of(List.<Item>of(), Cursor.first(10), Item::id).items()).isEmpty();
    assertThat(new CursorPage<Item>(null, null).items()).isEmpty();
  }
}
