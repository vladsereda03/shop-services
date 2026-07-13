-- Initial schema for the payments DB, matching the Subscription entity mapping.

CREATE SEQUENCE subscription_seq
    START WITH 1
    INCREMENT BY 1;

CREATE TABLE subscription (
    id            bigint NOT NULL,
    user_id       bigint NOT NULL,
    phone         character varying(255) NOT NULL,
    currency_code character varying(255) NOT NULL,
    periodicity   character varying(255) NOT NULL,
    start_date    timestamp(6) without time zone NOT NULL,
    CONSTRAINT subscription_pkey PRIMARY KEY (id)
);

CREATE TABLE subscription_items (
    subscription_id bigint  NOT NULL,
    good_id         bigint  NOT NULL,
    quantity        integer NOT NULL,
    price_kopeck    bigint  NOT NULL,
    CONSTRAINT subscription_items_pkey PRIMARY KEY (subscription_id, good_id),
    CONSTRAINT fk_subscription_items_subscription FOREIGN KEY (subscription_id) REFERENCES subscription (id)
);
