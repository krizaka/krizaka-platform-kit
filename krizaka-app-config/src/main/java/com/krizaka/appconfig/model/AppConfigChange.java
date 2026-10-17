package com.krizaka.appconfig.model;

import java.time.Instant;

/**
 * One line of the audit trail: a published revision.
 *
 * @param config the parameters as they were published
 * @param reason why they were published
 * @param changedBy who published them
 * @param changedAt when
 */
public record AppConfigChange(
    AppConfig config, String reason, String changedBy, Instant changedAt) {}
