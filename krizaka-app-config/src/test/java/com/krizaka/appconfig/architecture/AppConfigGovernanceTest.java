package com.krizaka.appconfig.architecture;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import com.krizaka.test.architecture.ArchitectureRules;
import com.krizaka.test.architecture.CodeRules;
import com.tngtech.archunit.core.domain.JavaClasses;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** The structure of {@code krizaka-app-config}: the rules that apply to a library are strict. */
class AppConfigGovernanceTest {

  private static final String PKG = "com.krizaka.appconfig";
  private static JavaClasses classes;

  @BeforeAll
  static void importClasses() {
    classes = CodeRules.importProductionClasses(PKG);
  }

  @Test
  @DisplayName("No System.out / System.err: output goes through SLF4J")
  void noStandardStreams() {
    assertDoesNotThrow(() -> CodeRules.assertNoStandardStreams(classes));
  }

  @Test
  @DisplayName("No field injection: dependencies come through the constructor")
  void noFieldInjection() {
    assertDoesNotThrow(() -> CodeRules.assertNoFieldInjection(classes, PKG));
  }

  @Test
  @DisplayName("No cycle between the packages of the module")
  void noPackageCycles() {
    assertDoesNotThrow(() -> ArchitectureRules.noPackageCyclesRule(PKG).check(classes));
  }

  @Test
  @DisplayName("The environment is read in configuration only")
  void environmentOnlyInConfig() {
    assertDoesNotThrow(
        () -> ArchitectureRules.noEnvironmentOutsideConfigRule("..config..").check(classes));
  }
}
