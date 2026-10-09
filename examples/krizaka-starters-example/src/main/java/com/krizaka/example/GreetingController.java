package com.krizaka.example;

import com.krizaka.web.problem.NotFoundException;
import java.util.Map;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** One resource, to show a token reaching a controller and a domain error leaving it. */
@RestController
@RequestMapping("/api/v1/greetings")
public class GreetingController {

  /** Spring instantiates it. */
  public GreetingController() {}

  /**
   * Greets the caller named by the token.
   *
   * @param jwt the verified token
   * @return a greeting
   */
  @GetMapping
  public Map<String, String> greet(@AuthenticationPrincipal Jwt jwt) {
    return Map.of("greeting", "Hello, " + jwt.getSubject());
  }

  /**
   * There is no stored greeting: always a Problem Details 404.
   *
   * @param id the greeting asked for
   * @return never
   */
  @GetMapping("/{id}")
  public Map<String, String> greeting(@PathVariable String id) {
    throw new NotFoundException("greeting-not-found", "No greeting " + id + ".");
  }
}
