package com.krizaka.appconfig.web;

import com.krizaka.appconfig.model.AppConfigPayload;
import com.krizaka.appconfig.model.AppConfigPolicy;
import com.krizaka.appconfig.model.AppVersion;
import com.krizaka.appconfig.model.ClientPlatform;
import com.krizaka.appconfig.store.AppConfigReader;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.WebRequest;
import tools.jackson.databind.json.JsonMapper;

/**
 * {@code GET /api/v1/app-config?platform=ios&version=1.4.0&build=42&locale=fr}: public, cacheable
 * (a CDN may keep it for {@code max-age}), and answering from the database parameters. The only
 * required input is the platform; without a version the update mode is the operator's override or
 * {@code none}.
 */
@RestController
@RequestMapping(AppConfigPaths.ENDPOINT)
public class AppConfigController {

  private final AppConfigReader reader;
  private final JsonMapper json;
  private final Duration maxAge;

  /**
   * Creates the controller.
   *
   * @param reader the parameters
   * @param json the mapper (the ETag is a hash of the body)
   * @param maxAge the public {@code max-age}
   */
  public AppConfigController(AppConfigReader reader, JsonMapper json, Duration maxAge) {
    this.reader = Objects.requireNonNull(reader, "reader");
    this.json = Objects.requireNonNull(json, "json");
    this.maxAge = Objects.requireNonNull(maxAge, "maxAge");
  }

  /**
   * The parameters for a client.
   *
   * @param platform the platform ({@code ios}, {@code android}, {@code macos}…); the {@code
   *     X-Client-Platform} header is used when the query has none
   * @param version the app's version, optional
   * @param build the build number, optional and informational
   * @param locale the language wanted (two letters), optional; defaults to {@code Accept-Language}
   * @param platformHeader {@code X-Client-Platform}
   * @param acceptLanguage {@code Accept-Language}
   * @param request the request, for conditional GET
   * @return the payload, {@code 304} when the client's copy is current, {@code 400} for an unknown
   *     platform or version
   */
  @GetMapping(
      produces = {MediaType.APPLICATION_JSON_VALUE, MediaType.APPLICATION_PROBLEM_JSON_VALUE})
  public ResponseEntity<?> get(
      @RequestParam(name = "platform", required = false) String platform,
      @RequestParam(name = "version", required = false) String version,
      @RequestParam(name = "build", required = false) String build,
      @RequestParam(name = "locale", required = false) String locale,
      @RequestHeader(name = ClientHeaders.PLATFORM, required = false) String platformHeader,
      @RequestHeader(name = HttpHeaders.ACCEPT_LANGUAGE, required = false) String acceptLanguage,
      WebRequest request) {
    Optional<ClientPlatform> parsed =
        ClientPlatform.parse(platform != null ? platform : platformHeader);
    if (parsed.isEmpty()) {
      return problem(
          "unknown-platform", "platform must be one of ios, android, macos, windows, linux, web.");
    }
    AppVersion client = null;
    if (version != null && !version.isBlank()) {
      client = AppVersion.parse(version).orElse(null);
      if (client == null) {
        return problem("invalid-version", "version must look like 1.4.0.");
      }
    }
    String language = locale != null && !locale.isBlank() ? locale : languageOf(acceptLanguage);
    AppConfigPayload payload = AppConfigPolicy.payload(reader.get(parsed.get()), client, language);
    String etag = "\"" + hash(json.writeValueAsString(payload)) + "\"";
    CacheControl cache =
        CacheControl.maxAge(maxAge).cachePublic().staleWhileRevalidate(maxAge.multipliedBy(5));
    if (request.checkNotModified(etag)) {
      return ResponseEntity.status(HttpStatus.NOT_MODIFIED).cacheControl(cache).eTag(etag).build();
    }
    return ResponseEntity.ok()
        .cacheControl(cache)
        .eTag(etag)
        .header(HttpHeaders.VARY, HttpHeaders.ACCEPT_LANGUAGE)
        .header(ClientHeaders.CONTRACT, Integer.toString(payload.contract().max()))
        .body(payload);
  }

  private static String languageOf(String acceptLanguage) {
    if (acceptLanguage == null || acceptLanguage.isBlank()) {
      return null;
    }
    String first = acceptLanguage.split(",")[0].trim();
    return first.length() >= 2 ? first.substring(0, 2) : null;
  }

  private static ResponseEntity<Map<String, Object>> problem(String code, String detail) {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("title", "Bad Request");
    body.put("status", 400);
    body.put("code", code);
    body.put("detail", detail);
    return ResponseEntity.badRequest().contentType(MediaType.APPLICATION_PROBLEM_JSON).body(body);
  }

  private static String hash(String text) {
    try {
      byte[] digest =
          MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(digest, 0, 12);
    } catch (NoSuchAlgorithmException impossible) {
      throw new IllegalStateException(impossible);
    }
  }
}
