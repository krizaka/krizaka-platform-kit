package com.orazaka.notificationservice.application.service;

import com.orazaka.notification.domain.model.NotificationRequest;
import com.orazaka.notificationservice.domain.exception.TemplateNotFoundException;
import com.orazaka.notificationservice.domain.model.Notification;
import com.orazaka.notificationservice.infrastructure.config.NotificationProperties;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;

/**
 * Renders a {@link NotificationRequest} into a {@link Notification}.
 *
 * <p>A template is {@code <location>/<template>/<locale>.txt}: an optional first line {@code
 * Subject: …}, then the body. {@code {{name}}} placeholders are replaced by the request's
 * variables. The requested locale falls back to {@code en}. A placeholder with no value fails the
 * rendering — a message that went out with {@code {{verificationUrl}}} in it is worse than one that
 * went to the dead-letter queue.
 *
 * <p>The location defaults to the templates shipped in this jar; an application points {@code
 * orazaka.notifications.template-location} at its own directory to brand them without a rebuild.
 */
@Service
public class NotificationTemplateService {

  static final String DEFAULT_LOCALE = "en";
  private static final String SUBJECT_PREFIX = "Subject:";
  private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\{\\s*([a-zA-Z0-9_]+)\\s*}}");

  private final ResourceLoader resourceLoader;
  private final String location;
  private final Map<String, Optional<String>> cache = new ConcurrentHashMap<>();

  public NotificationTemplateService(
      ResourceLoader resourceLoader, NotificationProperties properties) {
    this.resourceLoader = resourceLoader;
    this.location = properties.templateLocation();
  }

  /**
   * Renders the request's template for its locale.
   *
   * @param request what to render
   * @return the rendered notification
   * @throws TemplateNotFoundException when neither the locale nor {@code en} has the template
   * @throws IllegalArgumentException when the template names a variable the request did not carry
   */
  public Notification render(NotificationRequest request) {
    String source =
        load(request.template(), request.locale())
            .or(() -> load(request.template(), DEFAULT_LOCALE))
            .orElseThrow(() -> new TemplateNotFoundException(request.template(), request.locale()));

    String subject = "";
    String body = source;
    if (source.startsWith(SUBJECT_PREFIX)) {
      int end = source.indexOf('\n');
      subject = (end < 0 ? source : source.substring(0, end)).substring(SUBJECT_PREFIX.length());
      body = end < 0 ? "" : source.substring(end + 1);
    }
    return new Notification(
        request.channel(),
        request.recipient(),
        request.template(),
        substitute(subject, request),
        substitute(body.strip(), request));
  }

  private Optional<String> load(String template, String locale) {
    return cache.computeIfAbsent(template + "/" + locale, key -> read(location + key + ".txt"));
  }

  private Optional<String> read(String path) {
    Resource resource = resourceLoader.getResource(path);
    if (!resource.exists()) {
      return Optional.empty();
    }
    try {
      return Optional.of(resource.getContentAsString(StandardCharsets.UTF_8));
    } catch (IOException unreadable) {
      throw new UncheckedIOException("Cannot read notification template " + path, unreadable);
    }
  }

  private static String substitute(String text, NotificationRequest request) {
    Matcher matcher = PLACEHOLDER.matcher(text);
    StringBuilder out = new StringBuilder();
    while (matcher.find()) {
      String name = matcher.group(1);
      String value = request.variables().get(name);
      if (value == null) {
        throw new IllegalArgumentException(
            "Template '" + request.template() + "' needs variable '" + name + "'");
      }
      matcher.appendReplacement(out, Matcher.quoteReplacement(value));
    }
    matcher.appendTail(out);
    return out.toString();
  }
}
