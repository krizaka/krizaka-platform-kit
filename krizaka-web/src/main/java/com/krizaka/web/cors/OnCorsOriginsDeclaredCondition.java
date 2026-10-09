package com.krizaka.web.cors;

import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionOutcome;
import org.springframework.boot.autoconfigure.condition.SpringBootCondition;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

/**
 * Matches when {@code krizaka.web.cors.allowed-origins} lists at least one origin, whichever way it
 * is written — a YAML list, indexed properties, or one comma-separated value ({@code
 * KRIZAKA_WEB_CORS_ALLOWED_ORIGINS=https://a,https://b}), which a test on {@code
 * allowed-origins[0]} would miss.
 */
public class OnCorsOriginsDeclaredCondition extends SpringBootCondition {

  /** The property this condition reads. */
  public static final String PROPERTY = "krizaka.web.cors.allowed-origins";

  /** Creates the condition. */
  public OnCorsOriginsDeclaredCondition() {}

  @Override
  public ConditionOutcome getMatchOutcome(
      ConditionContext context, AnnotatedTypeMetadata metadata) {
    List<String> origins =
        Binder.get(context.getEnvironment())
            .bind(PROPERTY, Bindable.listOf(String.class))
            .orElse(List.of());
    return origins.isEmpty()
        ? ConditionOutcome.noMatch(PROPERTY + " is empty: no browser origin is allowed")
        : ConditionOutcome.match(PROPERTY + " declares " + origins.size() + " origin(s)");
  }
}
