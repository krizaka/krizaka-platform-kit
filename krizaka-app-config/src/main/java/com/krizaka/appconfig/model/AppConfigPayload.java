package com.krizaka.appconfig.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.Map;

/**
 * What {@code GET /api/v1/app-config} returns, and the {@code appConfig} member of a {@code 426}.
 * The shape is the shared contract of every Krizaka client (the JSON schema is {@code
 * contract/app-config.schema.json}).
 *
 * @param platform the platform asked about
 * @param minSupportedVersion the oldest version allowed to run
 * @param latestVersion the newest published version
 * @param updateMode {@code none}, {@code soft} or {@code force}, for the version the client sent
 * @param storeUrl where to update, or {@code null}
 * @param maintenance the maintenance state
 * @param contract the contract range
 * @param flags the feature flags
 * @param message the update text in {@code locale}, or {@code null}
 * @param locale the language {@code message} and the maintenance message are in
 */
@JsonInclude(JsonInclude.Include.ALWAYS)
public record AppConfigPayload(
    String platform,
    String minSupportedVersion,
    String latestVersion,
    String updateMode,
    String storeUrl,
    Maintenance maintenance,
    Contract contract,
    Map<String, Object> flags,
    String message,
    String locale) {

  /**
   * Maintenance state.
   *
   * @param on whether the maintenance screen is shown
   * @param message the text in the payload's language, or {@code null}
   */
  public record Maintenance(boolean on, String message) {}

  /**
   * Contract range.
   *
   * @param min the oldest contract still served
   * @param max the newest contract spoken
   * @param sunset the day contracts older than {@code max} stop being served (ISO date), or {@code
   *     null}
   */
  public record Contract(int min, int max, String sunset) {}
}
