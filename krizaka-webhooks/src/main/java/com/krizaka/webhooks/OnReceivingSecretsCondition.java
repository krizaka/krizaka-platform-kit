package com.krizaka.webhooks;

import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionOutcome;
import org.springframework.boot.autoconfigure.condition.SpringBootCondition;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

/**
 * Matches when {@code krizaka.webhooks.receiving.secrets} lists a secret — as a YAML list or a
 * comma-separated value, which a plain property condition on {@code secrets[0]} would miss.
 */
class OnReceivingSecretsCondition extends SpringBootCondition {

  static final String PROPERTY = "krizaka.webhooks.receiving.secrets";

  @Override
  public ConditionOutcome getMatchOutcome(
      ConditionContext context, AnnotatedTypeMetadata metadata) {
    List<String> secrets =
        Binder.get(context.getEnvironment())
            .bind(PROPERTY, Bindable.listOf(String.class))
            .orElse(List.of());
    return secrets.stream().anyMatch(s -> s != null && !s.isBlank())
        ? ConditionOutcome.match(PROPERTY + " lists a secret")
        : ConditionOutcome.noMatch(PROPERTY + " lists no secret");
  }
}
