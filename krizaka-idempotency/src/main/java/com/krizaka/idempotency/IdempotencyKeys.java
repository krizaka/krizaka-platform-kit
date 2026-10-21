package com.krizaka.idempotency;

import java.util.regex.Pattern;

/** The HTTP names of the contract and the rule a key follows. */
public final class IdempotencyKeys {

  /**
   * The request header carrying the key (IETF draft {@code draft-ietf-httpapi-idempotency-key}).
   */
  public static final String KEY_HEADER = "Idempotency-Key";

  /** The response header set to {@code true} on a replayed answer. */
  public static final String REPLAYED_HEADER = "Idempotent-Replayed";

  private static final Pattern VALID = Pattern.compile("[\\x21-\\x7E]{1,255}");

  private IdempotencyKeys() {}

  /**
   * Whether a key is 1 to 255 visible ASCII characters (a UUID, a ULID, a client-made string).
   *
   * @param key the key
   * @return {@code true} when it can be stored and compared safely
   */
  public static boolean isValid(String key) {
    return key != null && VALID.matcher(key).matches();
  }
}
