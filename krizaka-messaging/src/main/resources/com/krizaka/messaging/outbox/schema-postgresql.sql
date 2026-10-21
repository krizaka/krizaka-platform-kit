-- The outbox table of JdbcOutboxStore (krizaka.messaging.outbox.jdbc.enabled=true), PostgreSQL.
-- krizaka-spring-boot-starter-persistence runs it for you; otherwise run it with your migrations, renaming the table and its
-- index if you set krizaka.messaging.outbox.jdbc.table.
CREATE TABLE IF NOT EXISTS krizaka_outbox (
    id              UUID         PRIMARY KEY,
    seq             BIGINT       GENERATED ALWAYS AS IDENTITY,
    exchange        VARCHAR(100) NOT NULL,
    routing_key     VARCHAR(255) NOT NULL,
    message_id      VARCHAR(255) NOT NULL UNIQUE,
    payload         JSONB        NOT NULL,
    headers         JSONB        NOT NULL DEFAULT '{}'::jsonb,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    published_at    TIMESTAMPTZ,
    attempts        INT          NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS krizaka_outbox_pending ON krizaka_outbox (next_attempt_at) WHERE published_at IS NULL;
CREATE INDEX IF NOT EXISTS krizaka_outbox_published ON krizaka_outbox (published_at) WHERE published_at IS NOT NULL;
