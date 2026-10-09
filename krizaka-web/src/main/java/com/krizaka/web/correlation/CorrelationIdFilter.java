package com.krizaka.web.correlation;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Accepts or creates the request's {@value #HEADER}, sends it back, and makes it the {@link
 * CorrelationId} for the whole request.
 *
 * <p>A caller's id is kept as is when it is {@linkplain CorrelationId#isValid(String) valid}, so a
 * request can be followed across services; otherwise a random UUID replaces it. The id is also
 * stored as the request attribute {@link #ATTRIBUTE}, so the error dispatch that follows a failure
 * logs under the same id.
 */
public final class CorrelationIdFilter extends OncePerRequestFilter {

  /** The request and response header. */
  public static final String HEADER = "X-Request-Id";

  /** The request attribute holding the id chosen for this request. */
  public static final String ATTRIBUTE = CorrelationIdFilter.class.getName() + ".id";

  /** Creates the filter. */
  public CorrelationIdFilter() {}

  /**
   * Runs on the error dispatch too: the error page is part of the same request.
   *
   * @return {@code false}
   */
  @Override
  protected boolean shouldNotFilterErrorDispatch() {
    return false;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    String id = idOf(request);
    request.setAttribute(ATTRIBUTE, id);
    response.setHeader(HEADER, id);
    try (CorrelationId.Scope scope = CorrelationId.open(id)) {
      chain.doFilter(request, response);
    }
  }

  private static String idOf(HttpServletRequest request) {
    if (request.getAttribute(ATTRIBUTE) instanceof String chosen) {
      return chosen;
    }
    String received = request.getHeader(HEADER);
    return CorrelationId.isValid(received) ? received : UUID.randomUUID().toString();
  }
}
