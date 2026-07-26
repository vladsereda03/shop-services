-- Idempotency guard for scheduled recurring charges: one row per (subscription, period),
-- keyed `<subscriptionId>#<periodKey>`. The primary key turns a second tick for the same
-- billing period into a failed INSERT instead of a duplicate order. The LiqPay callback path
-- keys on payment_id (processed_callback) instead; this table covers the local schedule
-- emulator, which has no payment_id to deduplicate on.

CREATE TABLE subscription_charge (
    id         varchar(255) NOT NULL,
    charged_at timestamp(6) with time zone NOT NULL,
    CONSTRAINT subscription_charge_pkey PRIMARY KEY (id)
);
