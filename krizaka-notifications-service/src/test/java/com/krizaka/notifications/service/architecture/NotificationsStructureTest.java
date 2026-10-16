package com.krizaka.notifications.service.architecture;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import com.krizaka.test.architecture.ArchitectureRules;
import com.krizaka.test.architecture.FrozenRules;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The structure of {@code krizaka-notifications-service}: the shared {@link ArchitectureRules},
 * frozen. The violations that exist today are recorded in {@code archunit_store/} (committed); a
 * new one fails the build and the debt can only shrink. To fix one, fix the code and commit the
 * smaller store.
 */
class NotificationsStructureTest {

  private static final String PKG = "com.krizaka.notifications.service";
  private static JavaClasses classes;

  @BeforeAll
  static void importClasses() {
    // this module's own classes: its siblings are on the classpath, their debt is frozen in their
    // own store
    classes =
        new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .withImportOption(location -> location.contains("krizaka-notifications-service"))
            .importPackages(PKG);
  }

  @Test
  @DisplayName("No cycle between the packages of the module")
  void noPackageCycles() {
    assertDoesNotThrow(
        () -> {
          FrozenRules.check(ArchitectureRules.noPackageCyclesRule(PKG), classes);
        });
  }

  @Test
  @DisplayName("Controllers, services and repositories are named for what they are")
  void namingConventions() {
    assertDoesNotThrow(
        () -> {
          FrozenRules.check(ArchitectureRules.controllersNamedControllerRule(), classes);
          FrozenRules.check(ArchitectureRules.servicesNamedServiceRule(), classes);
          FrozenRules.check(ArchitectureRules.repositoriesNamedRepositoryRule(), classes);
        });
  }

  @Test
  @DisplayName("@Value and the environment are read in configuration only")
  void configurationOnlyInConfig() {
    assertDoesNotThrow(
        () -> {
          FrozenRules.check(ArchitectureRules.valueOnlyInConfigRule("..config.."), classes);
          FrozenRules.check(
              ArchitectureRules.noEnvironmentOutsideConfigRule("..config.."), classes);
        });
  }

  @Test
  @DisplayName("A transaction starts on a service only")
  void transactionalOnlyOnServices() {
    assertDoesNotThrow(
        () -> {
          FrozenRules.check(
              ArchitectureRules.transactionalOnlyOnServicesRule("..service.application.."),
              classes);
        });
  }
}
