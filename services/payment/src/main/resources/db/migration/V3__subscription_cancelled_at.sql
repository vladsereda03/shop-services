-- Subscription lifecycle: a soft-cancel timestamp (null = active). The scheduler only charges
-- subscriptions whose cancelled_at IS NULL. Kept as a nullable column rather than a hard delete so
-- the subscription history — and the moment a recurring charge was stopped — survives cancellation.

ALTER TABLE subscription ADD COLUMN cancelled_at timestamp(6) with time zone;
