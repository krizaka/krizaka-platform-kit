package com.krizaka.web.correlation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

class CorrelationIdTest {

  @AfterEach
  void clear() {
    MDC.clear();
  }

  @Test
  void isTheMdcValueWhileTheScopeIsOpen() {
    try (CorrelationId.Scope scope = CorrelationId.open("req-1")) {
      assertThat(CorrelationId.current()).isEqualTo("req-1");
      assertThat(MDC.get(CorrelationId.MDC_KEY)).isEqualTo("req-1");
    }
    assertThat(CorrelationId.current()).isNull();
  }

  @Test
  void closingANestedScopeRestoresTheOuterId() {
    try (CorrelationId.Scope outer = CorrelationId.open("outer")) {
      try (CorrelationId.Scope inner = CorrelationId.open("inner")) {
        assertThat(CorrelationId.current()).isEqualTo("inner");
      }
      assertThat(CorrelationId.current()).isEqualTo("outer");
    }
  }

  @Test
  void currentOrNewCreatesAnIdOnlyOutsideAScope() {
    assertThat(UUID.fromString(CorrelationId.currentOrNew())).isNotNull();
    assertThat(CorrelationId.current()).isNull();
    try (CorrelationId.Scope scope = CorrelationId.open("req-2")) {
      assertThat(CorrelationId.currentOrNew()).isEqualTo("req-2");
    }
  }

  @Test
  void refusesAnIdThatCouldForgeALogLine() {
    assertThat(CorrelationId.isValid("edge-7f3a:01J9/x+y=z@a.b_c")).isTrue();
    assertThat(CorrelationId.isValid("a".repeat(CorrelationId.MAX_LENGTH))).isTrue();
    assertThat(CorrelationId.isValid("a".repeat(CorrelationId.MAX_LENGTH + 1))).isFalse();
    assertThat(CorrelationId.isValid("a b")).isFalse();
    assertThat(CorrelationId.isValid("a\nb")).isFalse();
    assertThat(CorrelationId.isValid("")).isFalse();
    assertThat(CorrelationId.isValid(null)).isFalse();
    assertThatIllegalArgumentException().isThrownBy(() -> CorrelationId.open("a\"b"));
  }
}
