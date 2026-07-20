-- Transactional outbox for reliable event publishing.
-- A domain event is inserted in the SAME transaction as the state change it describes
-- (a new user), so the event can never be lost once the DB commit succeeds.
-- A scheduled publisher relays unpublished rows to Kafka and stamps published_at.

CREATE SEQUENCE outbox_seq
    START WITH 1
    INCREMENT BY 1;

CREATE TABLE outbox (
    id           bigint NOT NULL,
    aggregate_id character varying(255) NOT NULL,
    event_type   character varying(255) NOT NULL,
    topic        character varying(255) NOT NULL,
    payload      text NOT NULL,
    created_at   timestamp(6) with time zone NOT NULL,
    published_at timestamp(6) with time zone,
    CONSTRAINT outbox_pkey PRIMARY KEY (id)
);

-- the publisher polls this predicate every cycle; a partial index keeps the scan cheap
-- as published rows pile up
CREATE INDEX idx_outbox_unpublished ON outbox (id) WHERE published_at IS NULL;
