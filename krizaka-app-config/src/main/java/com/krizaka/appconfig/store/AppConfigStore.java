package com.krizaka.appconfig.store;

import com.krizaka.appconfig.model.AppConfig;
import com.krizaka.appconfig.model.AppConfigChange;
import com.krizaka.appconfig.model.ClientPlatform;
import java.util.List;
import java.util.Optional;

/**
 * Where the parameters are kept. Implementations: {@link JdbcAppConfigStore} (the database, the
 * default) and {@link InMemoryAppConfigStore} (tests and demos, declared explicitly).
 */
public interface AppConfigStore {

  /**
   * The published parameters of a platform.
   *
   * @param platform the platform
   * @return the parameters, or empty when none were ever published
   */
  Optional<AppConfig> find(ClientPlatform platform);

  /**
   * Every platform's published parameters.
   *
   * @return the rows, ordered by platform
   */
  List<AppConfig> findAll();

  /**
   * Publishes new parameters: becomes the platform's current revision and is recorded in the
   * history with its reason.
   *
   * @param config the new parameters (its revision, date and author are set by the store)
   * @param actor who publishes
   * @param reason why — required, it goes to the audit trail
   * @return the published revision
   */
  AppConfig publish(AppConfig config, String actor, String reason);

  /**
   * The audit trail of a platform, newest first.
   *
   * @param platform the platform
   * @return the published revisions with who, when and why
   */
  List<AppConfigChange> history(ClientPlatform platform);

  /**
   * Publishes the parameters of an earlier revision again, as a new revision.
   *
   * @param platform the platform
   * @param revision the revision to restore
   * @param actor who restores
   * @param reason why
   * @return the new revision, or empty when the revision does not exist
   */
  Optional<AppConfig> rollback(ClientPlatform platform, int revision, String actor, String reason);
}
