CREATE TABLE outbox (
    id           BIGSERIAL PRIMARY KEY,
    topic        VARCHAR(100) NOT NULL,
    msg_key      VARCHAR(64)  NOT NULL,
    payload      TEXT         NOT NULL,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    published_at TIMESTAMPTZ
);

CREATE INDEX idx_outbox_unpublished ON outbox (id) WHERE published_at IS NULL;