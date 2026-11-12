# Shipstream

Event-driven microservices platform built with **Java 25 · Spring Boot 3.4.5 · Kafka KRaft 3.9**.

[![CI](https://github.com/WickTech/shipstream/actions/workflows/ci.yml/badge.svg)](https://github.com/WickTech/shipstream/actions/workflows/ci.yml)

| Service | Coverage |
|---------|----------|
| order-service | ![coverage](https://img.shields.io/endpoint?url=https://raw.githubusercontent.com/WickTech/shipstream/badges/order-service/order-service.json) |
| shipment-service | ![coverage](https://img.shields.io/endpoint?url=https://raw.githubusercontent.com/WickTech/shipstream/badges/shipment-service/shipment-service.json) |
| tracking-service | ![coverage](https://img.shields.io/endpoint?url=https://raw.githubusercontent.com/WickTech/shipstream/badges/tracking-service/tracking-service.json) |
| notification-service | ![coverage](https://img.shields.io/endpoint?url=https://raw.githubusercontent.com/WickTech/shipstream/badges/notification-service/notification-service.json) |

---

## Architecture

```
                      ┌─────────────┐
          HTTP        │  api-gateway │  Spring Cloud Gateway
        ──────────►   │   :8090      │  Redis rate-limit
                      └──────┬──────┘
           ┌─────────────────┼──────────────────┐
           ▼                 ▼                  ▼
   ┌──────────────┐  ┌──────────────┐  ┌──────────────────┐
   │ order-service│  │shipment-svc  │  │ tracking-service │
   │    :8080     │  │   :8081      │  │     :8083        │
   └──────┬───────┘  └──────┬───────┘  └──────────────────┘
          │  orders.created  │  shipments.created / status_changed
          └──────────────────┴──────────────┐
                                            ▼
                                  ┌──────────────────┐
                                  │notification-svc  │
                                  │    :8084         │
                                  │ Valkey dedup+DLQ │
                                  └──────────────────┘
```

**Kafka topics** (KRaft, no ZooKeeper):
`orders.created` · `orders.cancelled` · `shipments.created` · `shipments.status_changed` · `tracking.events` · `notifications.dlq`

---

## Services

| Service | Port | Responsibility |
|---------|------|----------------|
| api-gateway | 8090 | Spring Cloud Gateway — routes + IP rate-limit |
| order-service | 8080 | CRUD orders + transactional outbox → Kafka |
| shipment-service | 8081 | Consume `orders.created`, own outbox, status updates |
| tracking-service | 8083 | Carrier webhook ingest (HMAC-SHA256), timeline API |
| notification-service | 8084 | Consume shipment events, Valkey dedup, DLQ |

---

## Tech Stack

| Layer | Choice |
|-------|--------|
| Language | Java 25 (`--enable-preview`) |
| Framework | Spring Boot 3.4.5 · Spring Cloud 2024.0.1 |
| Messaging | Apache Kafka KRaft 3.9 (no ZooKeeper) |
| Schema registry | Apicurio 3.0.7 (Avro schemas in `common/`) |
| Database | PostgreSQL 17 per service (Flyway migrations) |
| Caching / dedup | Valkey 8 (Redis-compatible) |
| Tracing | OTel → Jaeger (Micrometer bridge) |
| Metrics | Micrometer → Prometheus → Grafana 11 |
| Tests | JUnit 5 + Testcontainers + WireMock |
| Coverage | JaCoCo 0.8.13 (≥ 70% line, enforced in CI) |

---

## Quick Start

```bash
# Start all infrastructure (Kafka, Postgres, Valkey, Jaeger, Prometheus, Grafana)
docker-compose up -d

# Run a service (example)
export JAVA_HOME=~/.local/share/jdks/jdk-25
./mvnw -pl common,order-service -am spring-boot:run
```

tracking-service will not start without a webhook secret. For local runs use any
throwaway value of 16+ characters (dev only, never reuse it anywhere real):

```bash
export TRACKING_WEBHOOK_SECRET=dev-only-local-secret-change-me
./mvnw -pl common,tracking-service -am spring-boot:run
```

| UI | URL |
|----|-----|
| Grafana | http://localhost:3000 (admin / admin) |
| Jaeger | http://localhost:16686 |
| Prometheus | http://localhost:9090 |

---

## Carrier Webhook Signing

`POST /tracking/ingest` only accepts signed requests. The sender computes
`HMAC-SHA256(secret, "<timestamp>.<raw body>")`, where `<timestamp>` is Unix epoch
seconds, and sends two headers:

| Header | Value |
|--------|-------|
| `X-Shipstream-Timestamp` | the same epoch seconds used in the signature |
| `X-Shipstream-Signature` | `sha256=<lowercase hex digest>` |

Requests with a missing or wrong signature, or a timestamp more than
`TRACKING_WEBHOOK_TOLERANCE` (default `5m`) in the past or future, get `401`.
A signed body that fails validation gets `400`. Example sender:

```bash
SECRET=$TRACKING_WEBHOOK_SECRET
BODY='{"shipmentId":"8d2c6c3e-0b7a-4a51-9a39-5a3f4c1f2b10","carrierCode":"UPS","location":"Chicago, IL","description":"Picked up","occurredAt":"2026-01-01T12:00:00Z"}'
TS=$(date +%s)
SIG=$(printf '%s.%s' "$TS" "$BODY" | openssl dgst -sha256 -hmac "$SECRET" | sed 's/^.*= //')
curl -i http://localhost:8083/tracking/ingest \
  -H 'Content-Type: application/json' \
  -H "X-Shipstream-Timestamp: $TS" \
  -H "X-Shipstream-Signature: sha256=$SIG" \
  --data-raw "$BODY"
```

The timestamp window limits replay but does not stop a replay inside the window;
make ingest idempotent upstream if that matters.

---

## Exposure

This is a local demo stack. docker-compose publishes Kafka, Postgres, Valkey and the
UIs on the host with default credentials (`shipstream/shipstream`, Grafana
`admin/admin`), and none of the services authenticate callers except the tracking
webhook. Before exposing anything beyond your machine:

- Set a real `TRACKING_WEBHOOK_SECRET` (e.g. `openssl rand -hex 32`) and `DB_PASS`;
  replace the dev placeholders in `k8s/secret.yaml` (or create the secret out of band).
- Change the Grafana admin password and disable anonymous access.
- Put authentication in front of the api-gateway (it only rate-limits) and do not
  publish Kafka, Postgres or Valkey ports.
- Build and push images with the tag pinned in `k8s/*.yaml` (`0.1.0`), or bump it with
  `kustomize edit set image`.

---

## Running Tests

```bash
# All modules
./mvnw verify

# Single module
./mvnw -pl common,order-service -am verify
```

Testcontainers pulls Postgres 17, cp-kafka:7.8.0, and valkey:8 automatically.

---

## Load Testing

```bash
# Install k6: https://k6.io/docs/getting-started/installation/

# Smoke (30 s, 5 VUs)
k6 run --env BASE_URL=http://localhost:8090 k6/smoke.js

# Load (8 min ramp, 50 VUs peak)
k6 run --env BASE_URL=http://localhost:8090 k6/load.js
```

---

## Kubernetes

```bash
# Apply all manifests (requires images published to ghcr.io/wicktech/shipstream/*)
kubectl apply -k k8s/
```

Pods run as non-root (UID 1000) with a read-only root filesystem (`/tmp` is an
`emptyDir`), no privilege escalation, all capabilities dropped and the
`RuntimeDefault` seccomp profile. Images are pinned to `0.1.0`, not `latest`.

---

## Project Layout

```
shipstream/
├── common/               Avro schemas + generated classes
├── order-service/        Orders CRUD + outbox
├── shipment-service/     Shipment lifecycle
├── tracking-service/     Carrier webhook + timeline
├── notification-service/ Event consumer + Valkey dedup
├── api-gateway/          Spring Cloud Gateway
├── k6/                   Load test scripts
├── k8s/                  Kubernetes manifests (kustomize)
├── grafana/              Provisioning + dashboard JSON
├── scripts/              DB init SQL, Prometheus config
└── docker-compose.yml    Full local stack
```
