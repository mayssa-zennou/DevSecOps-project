CREATE TABLE orders (
    id          UUID PRIMARY KEY,
    product_id  VARCHAR(64) NOT NULL,
    quantity    INTEGER     NOT NULL CHECK (quantity > 0),
    status      VARCHAR(16) NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL
);