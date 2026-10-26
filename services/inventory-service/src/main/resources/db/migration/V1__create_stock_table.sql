CREATE TABLE stock (
    product_id VARCHAR(64) PRIMARY KEY,
    available  INTEGER NOT NULL CHECK (available >= 0)
);