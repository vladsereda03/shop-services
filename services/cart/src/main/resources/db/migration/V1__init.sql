-- Initial schema for the carts DB, matching the Cart entity mapping
-- (cart_items constraints are what Hibernate would generate on a fresh DB:
-- the dev DB created via ddl-auto:update lacked them and was aligned manually).

CREATE SEQUENCE cart_seq
    START WITH 1
    INCREMENT BY 1;

CREATE TABLE cart (
    id      bigint NOT NULL,
    user_id bigint NOT NULL,
    CONSTRAINT cart_pkey PRIMARY KEY (id)
);

CREATE TABLE cart_items (
    cart_id      bigint  NOT NULL,
    good_id      bigint  NOT NULL,
    quantity     integer NOT NULL,
    price_kopeck bigint  NOT NULL,
    CONSTRAINT cart_items_pkey PRIMARY KEY (cart_id, good_id),
    CONSTRAINT fk_cart_items_cart FOREIGN KEY (cart_id) REFERENCES cart (id)
);
