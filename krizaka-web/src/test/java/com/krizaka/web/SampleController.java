package com.krizaka.web;

import com.krizaka.web.correlation.CorrelationId;
import com.krizaka.web.problem.ConflictException;
import com.krizaka.web.problem.NotFoundException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** A controller that fails in every way the advice answers. */
@RestController
@RequestMapping("/sample")
public class SampleController {

  /** The body {@code POST /sample/items} validates. */
  public record ItemRequest(@NotBlank String name, @Positive int quantity) {}

  /** An exception that declares its own status. */
  @ResponseStatus(code = HttpStatus.TOO_MANY_REQUESTS, reason = "slow down")
  public static class TooManyRequests extends RuntimeException {
    private static final long serialVersionUID = 1L;
  }

  @GetMapping("/request-id")
  Map<String, String> requestId() {
    return Map.of("requestId", CorrelationId.current());
  }

  @GetMapping("/items/{id}")
  Map<String, String> item(@PathVariable String id) {
    throw new NotFoundException("item-not-found", "No item " + id + ".");
  }

  @PostMapping("/items")
  Map<String, String> create(@Valid @RequestBody ItemRequest request) {
    throw new ConflictException("Item " + request.name() + " already exists.");
  }

  @GetMapping("/search")
  Map<String, String> search(@RequestParam @Max(100) int limit) {
    return Map.of("limit", Integer.toString(limit));
  }

  @GetMapping("/boom")
  Map<String, String> boom() {
    throw new IllegalStateException("database password is hunter2");
  }

  @GetMapping("/throttled")
  Map<String, String> throttled() {
    throw new TooManyRequests();
  }

  @GetMapping("/denied")
  Map<String, String> denied() {
    throw new AccessDeniedException("no");
  }
}
