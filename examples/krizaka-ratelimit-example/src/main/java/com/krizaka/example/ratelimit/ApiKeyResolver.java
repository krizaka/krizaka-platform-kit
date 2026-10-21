package com.krizaka.example.ratelimit;

import com.krizaka.ratelimit.RateLimitKey;
import com.krizaka.ratelimit.RateLimitKeyResolver;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

/**
 * Limits by API key instead of by address, with a tier: a key starting {@code pro_} may make 10 a
 * minute, any other the configured default. Without a key, the caller's address.
 */
@Component
class ApiKeyResolver implements RateLimitKeyResolver {

  @Override
  public RateLimitKey resolve(HttpServletRequest request) {
    String apiKey = request.getHeader("X-Api-Key");
    if (apiKey == null || apiKey.isBlank()) {
      return RateLimitKey.of("ip:" + request.getRemoteAddr());
    }
    return new RateLimitKey("key:" + apiKey, apiKey.startsWith("pro_") ? 10 : null);
  }
}
