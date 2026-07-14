-- Runs once on the first start of the postgres container (empty data volume):
-- creates the five service databases; each service then migrates its own schema with Flyway.
CREATE DATABASE "authDB";
CREATE DATABASE products;
CREATE DATABASE carts;
CREATE DATABASE orders;
CREATE DATABASE payments;
