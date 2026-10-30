package com.krizaka.notifications.architecture;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import com.krizaka.test.architecture.ArchitectureRules;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The structure of {@code krizaka-notifications-api}: the shared {@link ArchitectureRules}, all
 * checked strictly (no debt found). A rule that fails later is fixed, or (existing debt only)
 * frozen with {@code FrozenRules}.
 */
class NotificationsApiStructureTest {

  private static final String PKG = "com.krizaka.notifications";
  private static JavaClasses classes;

  @BeforeAll
  static void importClasses() {
    // this module's own classes: its siblings are on the classpath, their debt is frozen in their
    // own store
    classes =
        new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .withImportOption(location -> location.contains("krizaka-notifications-api"))
            .importPackages(PKG);
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
        () -> {
          ArchitectureRules.noEnvironmentOutsideConfigRule("..config..").check(classes);
        });
  }
}
