CREATE TABLE IF NOT EXISTS orders (
    id         UUID PRIMARY KEY,
    customer   VARCHAR(255) NOT NULL,
    sku        VARCHAR(64)  NOT NULL,
    quantity   INT          NOT NULL,
    created_at TIMESTAMPTZ  NOT NULL
);
