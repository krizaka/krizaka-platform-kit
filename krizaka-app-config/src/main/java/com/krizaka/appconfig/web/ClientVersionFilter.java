package com.krizaka.appconfig.web;

import com.krizaka.appconfig.model.AppConfig;
import com.krizaka.appconfig.model.AppConfigPayload;
import com.krizaka.appconfig.model.AppConfigPolicy;
import com.krizaka.appconfig.model.AppVersion;
import com.krizaka.appconfig.model.ClientPlatform;
import com.krizaka.appconfig.model.UpdateMode;
import com.krizaka.appconfig.store.AppConfigReader;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.json.JsonMapper;

/**
 * Reads {@code X-Client-Version}, {@code X-Client-Platform} and {@code X-Contract-Version}, refuses
 * an app below the minimum with {@code 426 Upgrade Required} (a Problem Details body carrying the
 * {@code appConfig} payload, so the client can show the update screen from the failure alone), and
 * keeps N-1 contract compatibility: a contract older than the newest but still served is answered
 * with {@code Deprecation} and, when the date is announced, {@code Sunset}.
 *
 * <p>A request without the headers is not an app (a browser, another service, curl) and passes
 * untouched. So does a platform or a version the filter cannot read: the guard only ever refuses
 * what it understands.
 */
public final class ClientVersionFilter extends OncePerRequestFilter {

  private static final Logger LOG = LoggerFactory.getLogger(ClientVersionFilter.class);
  private static final AntPathMatcher MATCHER = new AntPathMatcher();
  private static final DateTimeFormatter HTTP_DATE =
      DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm:ss 'GMT'", Locale.ENGLISH)
          .withZone(ZoneOffset.UTC);

  private final AppConfigReader reader;
  private final JsonMapper json;
  private final List<String> include;
  private final List<String> exclude;
  private final String problemBase;
  private final Clock clock;

  /**
   * Creates the filter.
   *
   * @param reader the parameters
   * @param json the mapper writing the Problem Details body
   * @param include ant patterns of the guarded paths
   * @param exclude ant patterns that are never guarded
   * @param problemBase the prefix of the Problem Details {@code type}, ending with {@code /}
   * @param clock the clock (for the sunset check)
   */
  public ClientVersionFilter(
      AppConfigReader reader,
      JsonMapper json,
      List<String> include,
      List<String> exclude,
      String problemBase,
      Clock clock) {
    this.reader = Objects.requireNonNull(reader, "reader");
    this.json = Objects.requireNonNull(json, "json");
    this.include = List.copyOf(include);
    this.exclude = List.copyOf(exclude);
    this.problemBase = Objects.requireNonNull(problemBase, "problemBase");
    this.clock = Objects.requireNonNull(clock, "clock");
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    String path = request.getRequestURI().substring(request.getContextPath().length());
    return "OPTIONS".equals(request.getMethod())
        || exclude.stream().anyMatch(pattern -> MATCHER.match(pattern, path))
        || include.stream().noneMatch(pattern -> MATCHER.match(pattern, path));
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    Optional<ClientPlatform> platform =
        ClientPlatform.parse(request.getHeader(ClientHeaders.PLATFORM));
    if (platform.isEmpty()) {
      chain.doFilter(request, response);
      return;
    }
    AppConfig config = reader.get(platform.get());
    AppVersion version = AppVersion.parse(request.getHeader(ClientHeaders.VERSION)).orElse(null);
    OptionalInt contract = contractOf(request);
    response.setHeader(ClientHeaders.CONTRACT, Integer.toString(config.contractMax()));

    AppConfigPolicy.Verdict verdict = AppConfigPolicy.verdict(config, version, contract);
    if (verdict != AppConfigPolicy.Verdict.OK) {
      refuse(request, response, config, version, verdict);
      return;
    }
    if (AppConfigPolicy.deprecated(config, contract)) {
      response.setHeader(ClientHeaders.DEPRECATION, "true");
      if (config.contractSunset() != null) {
        response.setHeader(
            ClientHeaders.SUNSET,
            HTTP_DATE.format(config.contractSunset().plusDays(1).atStartOfDay(ZoneOffset.UTC)));
      }
    }
    chain.doFilter(request, response);
  }

  private void refuse(
      HttpServletRequest request,
      HttpServletResponse response,
      AppConfig config,
      AppVersion version,
      AppConfigPolicy.Verdict verdict)
      throws IOException {
    String language = language(request);
    AppConfigPayload payload = AppConfigPolicy.payload(config, version, language);
    // A 426 always tells the client to force: the guard refused the request, whatever the prompt
    // mode.
    AppConfigPayload body =
        new AppConfigPayload(
            payload.platform(),
            payload.minSupportedVersion(),
            payload.latestVersion(),
            UpdateMode.FORCE.wire(),
            payload.storeUrl(),
            payload.maintenance(),
            payload.contract(),
            payload.flags(),
            payload.message(),
            payload.locale());
    LOG.info(
        "426 {} {} client={} platform={} contract={}",
        request.getMethod(),
        request.getRequestURI(),
        version,
        config.platform().wire(),
        request.getHeader(ClientHeaders.CONTRACT));
    String code =
        verdict == AppConfigPolicy.Verdict.CONTRACT_TOO_OLD
            ? "contract-unsupported"
            : "upgrade-required";
    Map<String, Object> problem = new LinkedHashMap<>();
    problem.put("type", problemBase + code);
    problem.put("title", "Upgrade Required");
    problem.put("status", 426);
    problem.put("code", code);
    problem.put(
        "detail",
        verdict == AppConfigPolicy.Verdict.CONTRACT_TOO_OLD
            ? "This version of the app speaks an API contract that is no longer served. Update the app."
            : "This version of the app is no longer supported. Update the app.");
    problem.put("instance", request.getRequestURI());
    problem.put("appConfig", body);
    response.setStatus(426);
    response.setHeader("Upgrade", "krizaka-app/" + config.minSupportedVersion());
    response.setHeader("Cache-Control", "no-store");
    response.setContentType("application/problem+json");
    response.setCharacterEncoding(StandardCharsets.UTF_8.name());
    response.getOutputStream().write(json.writeValueAsBytes(problem));
  }

  private static OptionalInt contractOf(HttpServletRequest request) {
    String raw = request.getHeader(ClientHeaders.CONTRACT);
    if (raw == null) {
      return OptionalInt.empty();
    }
    try {
      int value = Integer.parseInt(raw.trim());
      return value >= 1 ? OptionalInt.of(value) : OptionalInt.empty();
    } catch (NumberFormatException notAContract) {
      return OptionalInt.empty();
    }
  }

  static String language(HttpServletRequest request) {
    String accept = request.getHeader("Accept-Language");
    if (accept == null || accept.isBlank()) {
      return null;
    }
    String first = accept.split(",")[0].trim();
    return first.length() >= 2 ? first.substring(0, 2).toLowerCase(Locale.ROOT) : null;
  }
}
