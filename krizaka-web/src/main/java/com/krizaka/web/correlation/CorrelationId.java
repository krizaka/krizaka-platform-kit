package com.krizaka.web.correlation;

import java.util.UUID;
import java.util.regex.Pattern;
import org.slf4j.MDC;

/**
 * The id of the request being served, kept in the logging MDC under {@value #MDC_KEY}.
 *
 * <p>Open a scope with try-with-resources; closing it restores what was there before, so a nested
 * scope (a job started from a request) never leaks its id into the caller's logs:
 *
 * <pre>{@code
 * try (CorrelationId.Scope scope = CorrelationId.open(message.getCorrelationId())) {
 *   handle(message);   // every log line carries requestId
 * }
 * }</pre>
 *
 * <p>{@link CorrelationIdFilter} opens one for every HTTP request; a consumer or a scheduled job
 * opens its own. It needs nothing but SLF4J.
 */
public final class CorrelationId {

  /** The MDC key the id is stored under — the field log encoders and dashboards read. */
  public static final String MDC_KEY = "requestId";

  /** The longest id accepted from outside. */
  public static final int MAX_LENGTH = 128;

  private static final Pattern VALID = Pattern.compile("[A-Za-z0-9._:@/+=-]{1," + MAX_LENGTH + "}");

  private CorrelationId() {}

  /**
   * Makes {@code id} the current correlation id until the scope is closed.
   *
   * @param id the id; must be {@linkplain #isValid(String) valid}
   * @return the scope to close, which restores the previous id (or none)
   * @throws IllegalArgumentException when the id is not valid
   */
  public static Scope open(String id) {
    if (!isValid(id)) {
      throw new IllegalArgumentException("not a valid correlation id: '" + id + "'");
    }
    String previous = MDC.get(MDC_KEY);
    MDC.put(MDC_KEY, id);
    return new Scope(previous);
  }

  /**
   * The current correlation id.
   *
   * @return the id of the open scope, or {@code null} outside one
   */
  public static String current() {
    return MDC.get(MDC_KEY);
  }

  /**
   * The current correlation id, or a new one when there is none — for the producer of a message
   * that must carry an id whether or not a request started it. Does not open a scope.
   *
   * @return the current id, or a new random UUID
   */
  public static String currentOrNew() {
    String current = current();
    return current != null ? current : UUID.randomUUID().toString();
  }

  /**
   * Whether a value received from outside may be used as a correlation id: 1 to {@value
   * #MAX_LENGTH} characters among letters, digits and {@code . _ : @ / + = -}. Anything else —
   * spaces, control characters, quotes — could forge a log line, and is replaced instead.
   *
   * @param candidate the value received, possibly {@code null}
   * @return {@code true} when it can be used as is
   */
  public static boolean isValid(String candidate) {
    return candidate != null && VALID.matcher(candidate).matches();
  }

  /** An open correlation scope; closing it restores the previous id. */
  public static final class Scope implements AutoCloseable {

    private final String previous;

    private Scope(String previous) {
      this.previous = previous;
    }

    /** Restores the id that was current when the scope was opened. */
    @Override
    public void close() {
      if (previous == null) {
        MDC.remove(MDC_KEY);
      } else {
        MDC.put(MDC_KEY, previous);
      }
    }
  }
}
