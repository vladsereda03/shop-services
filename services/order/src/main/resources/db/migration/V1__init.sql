-- Initial schema for the orders DB, matching the Order entity mapping.

CREATE SEQUENCE order_seq
    START WITH 1
    INCREMENT BY 1;

CREATE TABLE orders (
    id         bigint NOT NULL,
    user_id    bigint NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    CONSTRAINT orders_pkey PRIMARY KEY (id)
);

CREATE TABLE order_items (
    order_id     bigint  NOT NULL,
    good_id      bigint  NOT NULL,
    quantity     integer NOT NULL,
    price_kopeck bigint  NOT NULL,
    CONSTRAINT order_items_pkey PRIMARY KEY (order_id, good_id),
    CONSTRAINT fk_order_items_order FOREIGN KEY (order_id) REFERENCES orders (id)
);
