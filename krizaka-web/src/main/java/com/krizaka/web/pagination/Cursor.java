package com.krizaka.web.pagination;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * Where the next page starts: the key of the last item served and the page size — sent to the
 * client as an opaque base64url token.
 *
 * <p>Keyset pagination, not offsets: the query is {@code WHERE id > :after ORDER BY id LIMIT :limit
 * + 1} on an increasing key (a sequence, a UUIDv7, a timestamp-ordered id), which stays correct
 * while rows are inserted and costs the same on page 1 and page 10,000. The token is opaque so that
 * clients pass it back instead of building it; {@link #decode(String)} refuses anything it did not
 * produce, and the limit is capped at {@value #MAX_LIMIT}.
 *
 * <pre>{@code
 * @GetMapping
 * CursorPage<Item> list(@RequestParam(required = false) String cursor) {
 *   Cursor page = Cursor.fromRequest(cursor, 20);
 *   List<Item> rows = items.after(page.after(), page.limit() + 1); // one more than the page
 *   return CursorPage.of(rows, page, Item::id);
 * }
 * }</pre>
 *
 * @param after the key of the last item already served, or {@code null} for the first page
 * @param limit the page size, from 1 to {@value #MAX_LIMIT}
 */
public record Cursor(String after, int limit) {

  /** The largest page a client may ask for. */
  public static final int MAX_LIMIT = 100;

  /** The longest key a cursor carries. */
  public static final int MAX_KEY_LENGTH = 256;

  private static final String VERSION = "1";
  private static final char SEPARATOR = '|';
  private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
  private static final Base64.Decoder DECODER = Base64.getUrlDecoder();

  /**
   * Validates the position.
   *
   * @throws InvalidCursorException when the limit is outside 1..{@value #MAX_LIMIT} or the key is
   *     empty or longer than {@value #MAX_KEY_LENGTH} characters
   */
  public Cursor {
    if (limit < 1 || limit > MAX_LIMIT) {
      throw new InvalidCursorException("limit must be between 1 and " + MAX_LIMIT);
    }
    if (after != null && (after.isEmpty() || after.length() > MAX_KEY_LENGTH)) {
      throw new InvalidCursorException("cursor key must be 1 to " + MAX_KEY_LENGTH + " characters");
    }
  }

  /**
   * The first page.
   *
   * @param limit the page size, from 1 to {@value #MAX_LIMIT}
   * @return a cursor with no key
   */
  public static Cursor first(int limit) {
    return new Cursor(null, limit);
  }

  /**
   * The page after the item with this numeric key.
   *
   * @param after the key of the last item served, not negative
   * @param limit the page size, from 1 to {@value #MAX_LIMIT}
   * @return the cursor
   * @throws InvalidCursorException when the key is negative
   */
  public static Cursor startingAfter(long after, int limit) {
    if (after < 0) {
      throw new InvalidCursorException("cursor key must not be negative");
    }
    return new Cursor(Long.toString(after), limit);
  }

  /**
   * The cursor a request asks for: the decoded token, or the first page when there is none.
   *
   * @param token the {@code cursor} query parameter, possibly {@code null} or blank
   * @param defaultLimit the page size of a first page
   * @return the cursor
   * @throws InvalidCursorException when the token was not produced by {@link #encode()}
   */
  public static Cursor fromRequest(String token, int defaultLimit) {
    return token == null || token.isBlank() ? first(defaultLimit) : decode(token);
  }

  /**
   * Reads a token produced by {@link #encode()}.
   *
   * @param token the opaque token
   * @return the cursor it encodes
   * @throws InvalidCursorException for anything else — wrong alphabet, wrong version, a tampered
   *     value, or a non-canonical spelling of a valid one
   */
  public static Cursor decode(String token) {
    if (token == null || token.isEmpty() || token.length() > 2 * MAX_KEY_LENGTH) {
      throw new InvalidCursorException("malformed cursor");
    }
    String payload;
    try {
      payload = new String(DECODER.decode(token), StandardCharsets.UTF_8);
    } catch (IllegalArgumentException e) {
      throw new InvalidCursorException("malformed cursor");
    }
    String[] parts = payload.split("\\|", 3);
    if (parts.length < 2 || !VERSION.equals(parts[0])) {
      throw new InvalidCursorException("malformed cursor");
    }
    Cursor cursor;
    try {
      int limit = Integer.parseInt(parts[1]);
      cursor = new Cursor(parts.length == 3 ? parts[2] : null, limit);
    } catch (NumberFormatException e) {
      throw new InvalidCursorException("malformed cursor");
    }
    if (!cursor.encode().equals(token)) {
      throw new InvalidCursorException("malformed cursor");
    }
    return cursor;
  }

  /**
   * The opaque token to hand to the client.
   *
   * @return base64url, without padding
   */
  public String encode() {
    StringBuilder payload = new StringBuilder(VERSION).append(SEPARATOR).append(limit);
    if (after != null) {
      payload.append(SEPARATOR).append(after);
    }
    return ENCODER.encodeToString(payload.toString().getBytes(StandardCharsets.UTF_8));
  }

  /**
   * The key as a number, for tables keyed by a sequence.
   *
   * @return the key, or {@code null} on the first page
   * @throws InvalidCursorException when the key is not a non-negative integer
   */
  public Long afterAsLong() {
    if (after == null) {
      return null;
    }
    try {
      long value = Long.parseLong(after);
      if (value < 0) {
        throw new InvalidCursorException("cursor key must not be negative");
      }
      return value;
    } catch (NumberFormatException e) {
      throw new InvalidCursorException("cursor key is not a number");
    }
  }
}
