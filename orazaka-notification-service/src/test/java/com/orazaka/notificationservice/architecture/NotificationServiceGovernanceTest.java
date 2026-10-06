package com.orazaka.notificationservice.architecture;

import com.orazaka.test.architecture.ConfigBindingRules;
import com.orazaka.test.architecture.GovernanceRules;
import com.orazaka.test.architecture.LoggedContentRules;
import com.orazaka.test.architecture.PackPurityRules;
import com.orazaka.test.architecture.SourceFileScanner;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Governance guardrails for orazaka-notification-service. The notification context is autonomous
 * and stateless: it consumes events through copied contracts and must never couple in-process to
 * another bounded context's Tier-3 implementation (AGENTS.md §2, sharing tiers).
 */
class NotificationServiceGovernanceTest {

  /** Repository-wide rules need the whole workspace; standalone they are reported as skipped. */
  private static final Path REPOSITORY_ROOT =
      PackPurityRules.locateRepositoryRoot(Path.of(System.getProperty("user.dir")));

  private static final String BASE_PACKAGE = "com.orazaka.notificationservice";

  private static JavaClasses productionClasses;

  @BeforeAll
  static void importClasses() {
    productionClasses =
        new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages(BASE_PACKAGE);
  }

  // GOV-006: [DOOR-001] is not invoked here: this service exposes no inbound HTTP entry (an AMQP
  // consumer behind actuator only), so the rule would examine the empty set.

  @Test
  @DisplayName("[SEAM-002] notification-service depends on no foreign Tier-3 implementation")
  void dependsOnNoForeignTier3() {
    GovernanceRules.assertNoForeignTier3Dependency(productionClasses, BASE_PACKAGE);
  }

  @Test
  @DisplayName("[ADR-035] /internal/v1 demands the SERVICE authority, not merely authentication")
  void internalSurfaceDemandsServiceAuthority() {
    GovernanceRules.assertInternalSurfaceRequiresServiceAuthority(
        Path.of(System.getProperty("user.dir"), "src", "main", "java"));
  }

  @Test
  @DisplayName("[AGENTS.md §4] requests run on virtual threads")
  void requestsRunOnVirtualThreads() {
    GovernanceRules.assertVirtualThreadsEnabled(Path.of(System.getProperty("user.dir")));
  }

  @Test
  @DisplayName("[PACK-002] no pack, studio or pack-capability key is a literal in engine code")
  void noPackKeyLiteralsInEngineCode() {
    GovernanceRules.assertNoPackKeyLiterals(REPOSITORY_ROOT);
  }

  @Test
  @DisplayName("[PACK-003] engine code never branches on a pack identifier")
  void noPackKeyConditionalsInEngineCode() {
    GovernanceRules.assertNoPackKeyConditionals(REPOSITORY_ROOT);
  }

  @Test
  @DisplayName(
      "[CFG-001] every type the configuration binder builds has a constructor it can choose")
  void configurationBindsUnambiguously() {
    ConfigBindingRules.assertConfigurationBindsUnambiguously();
    ConfigBindingRules.assertInjectableComponentsHaveOneConstructor();
  }

  @Test
  @DisplayName("[ERR-113] No Environment injection in production beans")
  void noEnvironmentInjection() {
    SourceFileScanner.assertNoEnvironmentInjection(Path.of("src", "main", "java"));
  }

  /** [LOG-001] no logging call takes a prompt, a response body or a message text (ADR-064). */
  @Test
  void noLoggingCallTakesContent() {
    LoggedContentRules.assertNoLoggingCallTakesContent();
  }
}
