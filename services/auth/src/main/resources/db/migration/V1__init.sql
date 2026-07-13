-- Initial schema for authDB, matching the User entity mapping.
-- Includes the composite PK on user_roles that previously existed only as a manual tweak in the dev DB.

CREATE SEQUENCE user_seq
    START WITH 1
    INCREMENT BY 1;

CREATE TABLE users (
    id        bigint NOT NULL,
    username  character varying(255),
    password  character varying(255),
    email     character varying(255),
    full_name character varying(255),
    phone     character varying(255),
    cart_id   bigint,
    CONSTRAINT users_pkey PRIMARY KEY (id),
    CONSTRAINT uk_users_username UNIQUE (username)
);

CREATE TABLE user_roles (
    user_id bigint NOT NULL,
    roles   character varying(255) NOT NULL,
    CONSTRAINT user_roles_pkey PRIMARY KEY (user_id, roles),
    CONSTRAINT user_roles_roles_check CHECK (roles IN ('USER', 'ADMIN')),
    CONSTRAINT fk_user_roles_user FOREIGN KEY (user_id) REFERENCES users (id)
);

CREATE TABLE user_orders (
    user_id  bigint NOT NULL,
    order_id bigint,
    CONSTRAINT fk_user_orders_user FOREIGN KEY (user_id) REFERENCES users (id)
);
