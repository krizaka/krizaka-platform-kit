-- The claims table of JdbcMessageDedup (krizaka.messaging.dedup.store=jdbc), PostgreSQL.
-- krizaka-spring-boot-starter-persistence runs it for you; otherwise run it with your migrations, renaming the table and its
-- index if you set krizaka.messaging.dedup.table. The index on processed_at keeps the purge off a sequential scan.
CREATE TABLE IF NOT EXISTS processed_messages (
    consumer     VARCHAR(255) NOT NULL,
    message_id   VARCHAR(255) NOT NULL,
    processed_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    PRIMARY KEY (consumer, message_id)
);
CREATE INDEX IF NOT EXISTS processed_messages_processed_at ON processed_messages (processed_at);
