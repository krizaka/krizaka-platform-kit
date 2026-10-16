package com.krizaka.messaging.architecture;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import com.krizaka.test.architecture.ArchitectureRules;
import com.krizaka.test.architecture.CodeRules;
import com.tngtech.archunit.core.domain.JavaClasses;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The structure of {@code krizaka-messaging}: layers, controllers, services and repositories do not
 * apply to a library (no rule here passes over nothing, GOV-006), so the rules that do — no
 * standard streams, no field injection, no cycles, the environment read in configuration only — are
 * all checked strictly. A rule that fails later is fixed, or (existing debt only) frozen with
 * {@code FrozenRules}.
 */
class MessagingGovernanceTest {

  private static final String PKG = "com.krizaka.messaging";
  private static JavaClasses classes;

  @BeforeAll
  static void importClasses() {
    classes = CodeRules.importProductionClasses(PKG);
  }

  @Test
  @DisplayName("No System.out / System.err: output goes through SLF4J")
  void noStandardStreams() {
    assertDoesNotThrow(
        () -> {
          CodeRules.assertNoStandardStreams(classes);
        });
  }

  @Test
  @DisplayName("No field injection: dependencies come through the constructor")
  void noFieldInjection() {
    assertDoesNotThrow(
        () -> {
          CodeRules.assertNoFieldInjection(classes, PKG);
        });
  }

  @Test
  @DisplayName("No cycle between the packages of the module")
  void noPackageCycles() {
    assertDoesNotThrow(
        () -> {
          ArchitectureRules.noPackageCyclesRule(PKG).check(classes);
        });
  }

  @Test
  @DisplayName("The environment is read in configuration only")
  void environmentOnlyInConfig() {
    assertDoesNotThrow(
        () -> ArchitectureRules.noEnvironmentOutsideConfigRule("..config..").check(classes));
  }
}
