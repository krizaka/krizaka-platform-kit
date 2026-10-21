package com.krizaka.ratelimit;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Decides who a request is, for the limit. Declare a bean to replace the default (the client
 * address): the authenticated principal, an API key header, a tenant.
 */
@FunctionalInterface
public interface RateLimitKeyResolver {

  /**
   * The key of this request.
   *
   * @param request the request
   * @return its key, or {@code null} to leave it unlimited
   */
  RateLimitKey resolve(HttpServletRequest request);
}
