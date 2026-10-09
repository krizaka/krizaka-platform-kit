package com.krizaka.web.pagination;

import java.util.List;
import java.util.function.Function;

/**
 * One page of results and the cursor of the next one.
 *
 * <p>Serialised as {@code {"items": [...], "nextCursor": "..."}}; {@code nextCursor} is absent on
 * the last page.
 *
 * @param items the items of this page, at most the cursor's limit
 * @param nextCursor the token of the next page, or {@code null} when this is the last one
 * @param <T> the item type
 */
public record CursorPage<T>(List<T> items, String nextCursor) {

  /** Copies the items. */
  public CursorPage {
    items = items == null ? List.of() : List.copyOf(items);
  }

  /**
   * Builds the page from a query that fetched <b>one more</b> row than the cursor's limit: the
   * extra row proves a next page exists without a {@code COUNT}, and is not served.
   *
   * @param fetched up to {@code cursor.limit() + 1} rows, in key order
   * @param cursor the cursor the rows were fetched for
   * @param key the increasing key of a row
   * @param <T> the item type
   * @return the page, with a next cursor when {@code fetched} held more than the limit
   */
  public static <T> CursorPage<T> of(List<T> fetched, Cursor cursor, Function<T, ?> key) {
    if (fetched.size() <= cursor.limit()) {
      return new CursorPage<>(fetched, null);
    }
    List<T> items = fetched.subList(0, cursor.limit());
    String last = String.valueOf(key.apply(items.get(items.size() - 1)));
    return new CursorPage<>(items, new Cursor(last, cursor.limit()).encode());
  }

  /**
   * Whether another page follows.
   *
   * @return {@code true} when {@link #nextCursor()} is set
   */
  public boolean hasNext() {
    return nextCursor != null;
  }
}
