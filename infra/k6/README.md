# Load testing (k6)

Baseline and re-measure harness for the Р5 data/performance track. The script drives the
catalog read path (`GET /api/products`) — the hot path the MinIO and Redis tracks optimise —
and reports `http_req_duration` p95/p99. k6 also pushes its metrics into the existing
Prometheus via remote write, so runs are visible in Grafana.

## 1. Seed a comparable catalog

The dev database ships only the manufacturer seed, so add a fixed synthetic catalog first
(50 goods, each ~50 KB image). Run it once and reuse it for both the baseline and the
re-measure so the two compare like for like:

```bash
psql "postgresql://<user>:<pass>@localhost:5432/products" -f infra/k6/seed-catalog.sql
# full-stack compose publishes PostgreSQL on 5433 instead of 5432
```

## 2. Run

**Compose** (k6 as a container on the stack network, metrics into Prometheus):

```bash
docker compose --profile load run --rm k6
# tune load:  docker compose --profile load run --rm -e VUS=100 -e STEADY=2m k6
```

**Host mode** (k6 installed locally, services started with `java -jar`):

```bash
k6 run -e BASE_URL_PRODUCT=http://localhost:8082 -e BASE_URL_AUTH=http://auth.local:9000 \
       infra/k6/catalog.js
```

## 3. Read the numbers

- **stdout** — k6 prints the end-of-run summary, including `http_req_duration` p95/p99 and
  `data_received` (the payload weight the image bytes dominate). This is the primary source.
- **Grafana** — the same metrics arrive through Prometheus remote write (metric
  `k6_http_req_duration`); build a panel or import a k6 dashboard against the Prometheus
  datasource.

## Tunables (`-e KEY=VALUE`)

| Key | Default | Meaning |
|---|---|---|
| `VUS` | `50` | peak virtual users |
| `RAMP` | `30s` | ramp-up duration |
| `STEADY` | `1m` | steady-state duration at `VUS` |
| `BASE_URL_PRODUCT` | `http://product:8082` | product service base URL |
| `BASE_URL_AUTH` | `http://auth.local:9000` | auth (token endpoint) base URL |
| `CLIENT_ID` / `CLIENT_SECRET` | `cart-service` / `cart-service-secret` | client_credentials client holding `products.read` |

The token client is the existing `cart-service` registration (it already has the
`products.read` scope), so the harness needs no change to the auth server. A dedicated
read-only load client can be added later if the identity should be isolated.
