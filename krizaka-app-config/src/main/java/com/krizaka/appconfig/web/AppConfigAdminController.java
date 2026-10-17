package com.krizaka.appconfig.web;

import com.krizaka.appconfig.model.AppConfig;
import com.krizaka.appconfig.model.AppConfigUpdate;
import com.krizaka.appconfig.model.ClientPlatform;
import com.krizaka.appconfig.store.AppConfigReader;
import jakarta.servlet.http.HttpServletRequest;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The admin API of the parameters: list, read, publish with a reason, history, rollback. Every
 * write is audited (who, when, why) and visible at once to this instance; others see it within the
 * cache TTL. Fails closed: without an {@link AdminGate} that allows the caller, nothing is served.
 */
@RestController
@RequestMapping(AppConfigPaths.ADMIN)
public class AppConfigAdminController {

  private final AppConfigReader reader;
  private final AdminGate gate;

  /**
   * Creates the controller.
   *
   * @param reader the parameters
   * @param gate who may call
   */
  public AppConfigAdminController(AppConfigReader reader, AdminGate gate) {
    this.reader = Objects.requireNonNull(reader, "reader");
    this.gate = Objects.requireNonNull(gate, "gate");
  }

  /**
   * Every platform's published parameters.
   *
   * @param request the request
   * @return the rows
   */
  @GetMapping
  public ResponseEntity<Object> list(HttpServletRequest request) {
    if (gate.check(request) instanceof AdminGate.Denied denied) {
      return refuse(denied);
    }
    return ResponseEntity.ok(reader.store().findAll());
  }

  /**
   * One platform's published parameters (the defaults when none were published).
   *
   * @param platform the platform
   * @param request the request
   * @return the parameters
   */
  @GetMapping("/{platform}")
  public ResponseEntity<Object> get(@PathVariable String platform, HttpServletRequest request) {
    if (gate.check(request) instanceof AdminGate.Denied denied) {
      return refuse(denied);
    }
    Optional<ClientPlatform> parsed = ClientPlatform.parse(platform);
    if (parsed.isEmpty()) {
      return ResponseEntity.notFound().build();
    }
    ClientPlatform p = parsed.get();
    return ResponseEntity.ok(reader.store().find(p).orElseGet(() -> AppConfig.defaults(p)));
  }

  /**
   * Publishes new parameters.
   *
   * @param platform the platform
   * @param update the new values and the reason
   * @param request the request
   * @return the new revision
   */
  @PutMapping(value = "/{platform}", consumes = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<Object> put(
      @PathVariable String platform,
      @RequestBody AppConfigUpdate update,
      HttpServletRequest request) {
    if (!(gate.check(request) instanceof AdminGate.Allowed allowed)) {
      return refuse((AdminGate.Denied) gate.check(request));
    }
    Optional<ClientPlatform> parsed = ClientPlatform.parse(platform);
    if (parsed.isEmpty()) {
      return ResponseEntity.notFound().build();
    }
    return ResponseEntity.ok(
        reader.publish(update.toConfig(parsed.get()), allowed.actor(), update.reason()));
  }

  /**
   * The audit trail of a platform.
   *
   * @param platform the platform
   * @param request the request
   * @return the revisions, newest first
   */
  @GetMapping("/{platform}/history")
  public ResponseEntity<Object> history(@PathVariable String platform, HttpServletRequest request) {
    if (gate.check(request) instanceof AdminGate.Denied denied) {
      return refuse(denied);
    }
    Optional<ClientPlatform> parsed = ClientPlatform.parse(platform);
    if (parsed.isEmpty()) {
      return ResponseEntity.notFound().build();
    }
    return ResponseEntity.ok(reader.store().history(parsed.get()));
  }

  /**
   * Restores an earlier revision as a new one.
   *
   * @param platform the platform
   * @param revision the revision to restore
   * @param body the reason
   * @param request the request
   * @return the new revision, {@code 404} when there is no such revision
   */
  @PostMapping("/{platform}/rollback/{revision}")
  public ResponseEntity<Object> rollback(
      @PathVariable String platform,
      @PathVariable int revision,
      @RequestBody Map<String, String> body,
      HttpServletRequest request) {
    if (!(gate.check(request) instanceof AdminGate.Allowed allowed)) {
      return refuse((AdminGate.Denied) gate.check(request));
    }
    Optional<ClientPlatform> parsed = ClientPlatform.parse(platform);
    if (parsed.isEmpty()) {
      return ResponseEntity.notFound().build();
    }
    return reader
        .rollback(parsed.get(), revision, allowed.actor(), body.get("reason"))
        .<ResponseEntity<Object>>map(ResponseEntity::ok)
        .orElseGet(() -> ResponseEntity.notFound().build());
  }

  /**
   * A rejected value is a 400, with the reason.
   *
   * @param invalid the failure
   * @return the Problem Details
   */
  @ExceptionHandler(IllegalArgumentException.class)
  public ResponseEntity<Map<String, Object>> invalid(IllegalArgumentException invalid) {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("title", "Bad Request");
    body.put("status", 400);
    body.put("code", "invalid-app-config");
    body.put("detail", invalid.getMessage());
    return ResponseEntity.badRequest().contentType(MediaType.APPLICATION_PROBLEM_JSON).body(body);
  }

  private static ResponseEntity<Object> refuse(AdminGate.Denied denied) {
    return ResponseEntity.status(denied.status()).build();
  }
}
