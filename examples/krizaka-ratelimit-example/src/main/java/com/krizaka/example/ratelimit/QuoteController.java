package com.krizaka.example.ratelimit;

import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code GET /api/v1/quote}: limited by the starter before it runs; {@code /actuator/**} never is.
 */
@RestController
class QuoteController {

  @GetMapping("/api/v1/quote")
  Map<String, String> quote() {
    return Map.of("quote", "Measure twice, cut once.");
  }
}
