package com.krizaka.security.architecture;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import com.krizaka.test.architecture.ArchitectureRules;
import com.krizaka.test.architecture.CodeRules;
import com.tngtech.archunit.core.domain.JavaClasses;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The structure of {@code krizaka-security}: layers do not apply to a library, so the rules that do
 * — no standard streams, no field injection, no cycles, names, configuration only in config,
 * transactions only on services — are all checked strictly. A rule that fails later is fixed, or
 * (existing debt only) frozen with {@code FrozenRules}.
 */
class SecurityGovernanceTest {

  private static final String PKG = "com.krizaka.security";
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
  @DisplayName("Controllers, services and repositories are named for what they are")
  void namingConventions() {
    assertDoesNotThrow(
        () -> {
          ArchitectureRules.controllersNamedControllerRule().check(classes);
          ArchitectureRules.servicesNamedServiceRule().check(classes);
          ArchitectureRules.repositoriesNamedRepositoryRule().check(classes);
        });
  }

  @Test
  @DisplayName("@Value and the environment are read in configuration only")
  void configurationOnlyInConfig() {
    assertDoesNotThrow(
        () -> {
          ArchitectureRules.valueOnlyInConfigRule("..config..").check(classes);
          ArchitectureRules.noEnvironmentOutsideConfigRule("..config..").check(classes);
        });
  }

  @Test
  @DisplayName("A transaction starts on a service only")
  void transactionalOnlyOnServices() {
    assertDoesNotThrow(
        () -> {
          ArchitectureRules.transactionalOnlyOnServicesRule(
                  "..service..", "..outbox..", "..dedup..")
              .check(classes);
        });
  }
}
