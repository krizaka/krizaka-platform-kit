-- The table of krizaka-idempotency's JDBC store (krizaka.idempotency.store=jdbc), PostgreSQL.
-- Run it with your migrations (Flyway, Liquibase) or spring.sql.init.schema-locations; with another table name
-- (krizaka.idempotency.table), copy it and rename the table and its index.
CREATE TABLE IF NOT EXISTS idempotency_keys (
    scope       VARCHAR(100) NOT NULL,
    subject     VARCHAR(255) NOT NULL,
    idem_key    VARCHAR(255) NOT NULL,
    fingerprint CHAR(64)     NOT NULL,
    response    TEXT,
    created_at  TIMESTAMPTZ  NOT NULL,
    PRIMARY KEY (scope, subject, idem_key)
);
CREATE INDEX IF NOT EXISTS idempotency_keys_created_at ON idempotency_keys (created_at);
