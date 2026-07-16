-- Idempotency guard for LiqPay callbacks: one row per processed payment_id.
-- The primary key turns a duplicate callback into a failed INSERT instead of
-- a second order for the same money.

CREATE TABLE processed_callback (
    payment_id   bigint NOT NULL,
    processed_at timestamp(6) with time zone NOT NULL,
    CONSTRAINT processed_callback_pkey PRIMARY KEY (payment_id)
);
