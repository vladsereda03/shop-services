-- Idempotency key for orders created from a confirmed one-time LiqPay payment: the payment_id
-- (unique per charge). If a callback is redelivered after the order already committed (e.g. the
-- payment service crashed before recording the callback), this unique key rejects the second
-- insert instead of creating a duplicate order.
--
-- Nullable because recurring charges are emulated locally by the payment scheduler and carry no
-- LiqPay payment_id; PostgreSQL treats NULLs as distinct, so those keyless orders coexist freely.

ALTER TABLE orders ADD COLUMN payment_id bigint;

ALTER TABLE orders ADD CONSTRAINT uk_orders_payment_id UNIQUE (payment_id);
