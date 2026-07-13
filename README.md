# Shop Services

[![CI](https://github.com/vladsereda03/shop-services/actions/workflows/ci.yml/badge.svg)](https://github.com/vladsereda03/shop-services/actions/workflows/ci.yml)

An educational e-commerce project. Users browse a catalog, manage a cart, place orders,
pay via [LiqPay](https://www.liqpay.ua/) and set up recurring subscriptions;
administrators manage the catalog.

**Stack:** Java 21 · Spring Boot 3.5 · Spring Security / Spring Authorization Server
(OAuth2 + OIDC) · Spring Data JPA · PostgreSQL · Apache Kafka · Thymeleaf · Maven
(multi-module monorepo).

## Architecture

| Module | Port | Database | Responsibility |
|---|---|---|---|
| `services/auth` | 9000 | `authDB` | Authorization server (Spring Authorization Server): user registration, form login, OIDC provider, JWT issuing; publishes `user-registered` events to Kafka |
| `services/client` | 8080 | — | BFF / web UI (Thymeleaf): OAuth2 client, renders catalog, cart, orders, payment and subscription pages; calls the APIs below with the user's token |
| `services/product` | 8082 | `products` | Catalog: goods and manufacturers, stock reserve/release, admin-only good creation |
| `services/cart` | 8083 | `carts` | Cart REST API with stock reservation; creates a cart for each new user via Kafka |
| `services/order` | 8084 | `orders` | Orders: checkout from cart, order history, internal API for payment-driven order creation |
| `services/payment` | 8085 | `payments` | LiqPay integration: payment form, signed callbacks, subscriptions with a scheduled charge emulator |
| `libs/contracts` | — | — | Shared contracts (Kafka event payloads) |

### Security model

- End users authenticate at **auth** via OIDC (`authorization_code` + refresh
  tokens); **client** is the only OAuth2 login client and keeps tokens server-side (BFF pattern).
- Service-to-service calls use **`client_credentials`** clients (`cart-service`,
  `order-service`, `payment-service`) with fine-grained scopes
  (`products.read/write`, `carts.read/write`, `orders.write`).
- Every API service is a **resource server**: JWTs are validated against the auth
  server's JWKS. User identity travels in a custom `uid` claim, roles in an
  `authorities` claim (`ROLE_USER`, `ROLE_ADMIN`); admin endpoints require `ROLE_ADMIN`.
- LiqPay callbacks are anonymous by design and authenticated by the LiqPay
  signature (`base64(sha1(private_key + data + private_key))`) instead of tokens.

### Key flows

- **Checkout:** client → order `POST /orders/my` → order pulls the cart from cart
  service, creates the order, clears the cart without returning stock.
- **One-time payment:** cart page embeds a LiqPay form from payment → LiqPay calls
  back `POST /payment/new` → payment verifies the signature and triggers checkout
  in order.
- **Subscription:** client → payment `POST /subscriptions/my` → payment snapshots
  the cart, stores the subscription, (optionally) registers it in LiqPay and clears
  the cart; recurring charges create orders from the snapshot.

## Getting started

### Prerequisites

- Java 21, Maven 3.9+
- PostgreSQL on `localhost:5432` with databases `authDB`, `products`, `carts`,
  `orders`, `payments` (schemas are created by Hibernate on first run)
- Docker (for Kafka and the Testcontainers-based integration tests)

### 1. Hosts entries

Services address each other and the auth server by these names (required for the
OIDC issuer to match), add to your hosts file:

```
127.0.0.1 auth.local
127.0.0.1 product.local
127.0.0.1 cart.local
```

### 2. Environment variables

| Variable | Used by | Default | Purpose |
|---|---|---|---|
| `SPRING_DATASOURCE_USERNAME` / `SPRING_DATASOURCE_PASSWORD` | auth | — (required) | Credentials for `authDB` |
| `LIQPAY_PUBLIC_KEY` / `LIQPAY_PRIVATE_KEY` | payment | `sandbox_public_key` / `sandbox_private_key` | LiqPay merchant keys; sandbox keys from your LiqPay account are needed only to render the real payment form |
| `LIQPAY_SUBSCRIBE_ENABLED` | payment | `false` | Register subscriptions in the LiqPay API. Keep `false` with sandbox keys — the LiqPay sandbox does not support subscriptions, so the built-in scheduler emulates recurring charges instead |

Other services use `postgres`/`postgres` for their databases (dev defaults in
`application.yaml`).

### 3. Kafka

```
docker compose -f infra/docker/kafka/docker-compose.yaml up -d
```

Brokers are exposed on `localhost:29092/39092/49092`. Kafka is needed for user
registration (auth publishes an event, cart provisions the user's cart).

### 4. Build and run

```
mvn package
```

This also runs the test suite (Docker must be running for the cart integration
tests); use `mvn package -DskipTests` to skip it.

Then start each service (in any order):

```
java -jar services/auth/target/auth-1.0-SNAPSHOT.jar
java -jar services/product/target/product-1.0-SNAPSHOT.jar
java -jar services/cart/target/cart-1.0-SNAPSHOT.jar
java -jar services/order/target/order-1.0-SNAPSHOT.jar
java -jar services/payment/target/payment-1.0-SNAPSHOT.jar
java -jar services/client/target/client-1.0-SNAPSHOT.jar
```

Open **http://localhost:8080**, register a user and log in.

### 5. Seed data

- **Manufacturers** are seeded directly in the `products` database (there is no
  admin UI for them):
  ```sql
  INSERT INTO manufacturer (name, contacts, description) VALUES ('ACME', 'acme@example.com', '...');
  ```
- **Admin role** — grant it in `authDB` to unlock catalog management in the UI:
  ```sql
  INSERT INTO user_roles (user_id, roles)
  SELECT id, 'ADMIN' FROM users WHERE username = '<username>';
  ```
  (re-login after granting).

## Tests

```
mvn test
```

- **Unit tests** (no infrastructure needed): LiqPay callback processing in
  `payment` (signature verification, duplicate callbacks, status handling),
  subscription creation ordering and rollback in `payment`, catalog validation
  and stock reserve/release in `product`.
- **Integration tests** (require Docker): full Spring context against real
  PostgreSQL and Kafka started by [Testcontainers](https://testcontainers.com/),
  with the neighbour services stubbed at the HTTP level:
  - `auth` — registration persists the user and the `user-registered` event
    actually reaches the Kafka broker; token issuing and JWKS smoke tests.
  - `cart` — Kafka-driven cart provisioning, stock reservation with transaction
    rollback on conflict, resource-server security rules.
  - `order` — checkout turns the cart into an order and rolls back when the
    cart cannot be cleared; scope-protected internal API.
  - `payment` — subscription snapshot persistence and rollback, the recurring
    charge query, anonymous LiqPay callbacks with signature enforcement.
  - `product` — concurrent stock reservation against the pessimistic row lock
    (no overselling), role/scope authorization including the custom JWT
    authorities converter.

## Testing LiqPay locally

The payment callback URL (`http://localhost:8085/payment/new`) is not reachable
from the internet, so callbacks are emulated with a script that builds a properly
signed request:

```powershell
./scripts/emulate-liqpay-callback.ps1 -UserId 1            # successful payment
./scripts/emulate-liqpay-callback.ps1 -UserId 1 -BadSignature   # must be rejected with 403
./scripts/emulate-liqpay-callback.ps1 -UserId 1 -Status failure # accepted, no order created
```

The script and the payment service share the sandbox key defaults, so real LiqPay
keys are not required for callback testing.

Recurring subscription charges are emulated by scheduled jobs in the payment
service (`task.cron.*` in `services/payment/src/main/resources/application.yaml`,
default: daily/weekly/monthly/yearly at 15:00).

## Known limitations & roadmap

- No distributed transactions between services: operation ordering minimizes the
  damage window (local writes first, external calls last), but sagas with
  compensation / a transactional outbox are future work.
- Dev secrets (client secrets, DB passwords) are plain-text in configs — fine for
  a local demo, not for production.
- Planned next: Dockerfiles + full `docker-compose` for all services and
  databases, Kubernetes manifests, an API gateway.

## Repository layout

```
libs/contracts/       shared event contracts
services/<name>/      one Spring Boot app per service
infra/docker/kafka/   Kafka cluster for local development
scripts/              development utilities (LiqPay callback emulator)
```
