ALTER TABLE orders
    ADD COLUMN user_id VARCHAR(64) NOT NULL DEFAULT 'alice',
    ADD COLUMN user_email VARCHAR(255) NOT NULL DEFAULT 'alice@test.com';
