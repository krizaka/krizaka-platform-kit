package com.krizaka.notifications.service.architecture;

import com.krizaka.test.architecture.CodeRules;
import com.krizaka.test.architecture.ConfigBindingRules;
import com.krizaka.test.architecture.SourceRules;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Governance guardrails for krizaka-notifications-service. The notification context is autonomous
 * and stateless: it consumes events through copied contracts and must never couple in-process to
 * another service's implementation, nor on any product.
 */
class NotificationServiceGovernanceTest {

  private static final String BASE_PACKAGE = "com.krizaka.notifications.service";

  private static JavaClasses productionClasses;

  @BeforeAll
  static void importClasses() {
    productionClasses =
        new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages(BASE_PACKAGE);
  }

  @Test
  @DisplayName("[AGENTS.md §4] requests run on virtual threads")
  void requestsRunOnVirtualThreads() {
    SourceRules.assertVirtualThreadsEnabled(Path.of(System.getProperty("user.dir")));
  }

  @Test
  @DisplayName(
      "[CFG-001] every type the configuration binder builds has a constructor it can choose")
  void configurationBindsUnambiguously() {
    ConfigBindingRules.assertConfigurationBindsUnambiguously("com.krizaka");
    ConfigBindingRules.assertInjectableComponentsHaveOneConstructor("com.krizaka");
  }

  @Test
  @DisplayName("[ERR-113] No Environment injection in production beans")
  void noEnvironmentInjection() {
    SourceRules.assertNoEnvironmentInjection(Path.of("src", "main", "java"));
  }

  @Test
  @DisplayName("[ERR-102] Notifications depends on no product")
  void dependsOnNoProduct() {
    com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses()
        .that()
        .resideInAPackage("com.krizaka.notifications..")
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage("com.krizaka.orazaka..", "com.orazaka..", "com.orochia..")
        .because("a Krizaka building block depends on no product — products depend on it")
        .check(productionClasses);
  }

  @Test
  @DisplayName("[ERR-103] One top-level class per file")
  void oneClassPerFile() {
    CodeRules.assertOneTopLevelClassPerFile(productionClasses, BASE_PACKAGE);
  }

  @Test
  @DisplayName("[GOV-004/005] No standard streams, no field injection")
  void codeHygiene() {
    CodeRules.assertNoStandardStreams(productionClasses);
    CodeRules.assertNoFieldInjection(productionClasses, BASE_PACKAGE);
  }
}
