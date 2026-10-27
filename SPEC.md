# shipstream — Spec

**Status:** planned  
**Language:** Java 25 / Spring Boot 4  
**Messaging:** Apache Kafka KRaft (no ZooKeeper)  
**Cache:** Valkey (Redis-compatible, open source)  
**Estimated effort:** 10–14 sessions  
**GitHub:** WickTech/shipstream (public)

---

## Problem

Demonstrate a production-grade distributed order/shipping/notification system using modern Java, event-driven architecture, and the full open-source observability stack — portfolio showpiece for backend/distributed-systems roles.

---

## Services

| Service | Port | Responsibility |
|---|---|---|
| `api-gateway` | 8080 | Spring Cloud Gateway — routing, rate-limit, auth header forwarding |
| `order-service` | 8081 | Create/cancel orders, outbox publisher |
| `shipment-service` | 8082 | Assign carrier, update shipment status |
| `tracking-service` | 8083 | Ingest carrier tracking events, expose tracking timeline |
| `notification-service` | 8084 | Email/SMS stubs, consume events, idempotent dedup |
| `payment-service` | 8085 | walletd integration (P7, optional) |

---

## Data Model

### Order
```
order_id (UUID), customer_id, items (JSONB), total_cents,
status (PENDING→CONFIRMED→SHIPPED→DELIVERED→CANCELLED),
created_at, updated_at
```

### Shipment
```
shipment_id (UUID), order_id, carrier, tracking_number,
status (CREATED→PICKED_UP→IN_TRANSIT→DELIVERED→FAILED),
estimated_delivery, created_at, updated_at
```

### TrackingEvent
```
event_id (UUID), shipment_id, carrier_code, location, description,
occurred_at, ingested_at
```

### OutboxEvent (per service DB)
```
event_id (UUID), aggregate_type, aggregate_id, event_type,
payload (JSONB), published (bool), created_at
```

---

## Kafka Topics

| Topic | Producer | Consumer(s) |
|---|---|---|
| `orders.created` | order-service | shipment-service, notification-service |
| `orders.cancelled` | order-service | shipment-service, notification-service |
| `shipments.created` | shipment-service | notification-service |
| `shipments.status_changed` | shipment-service | tracking-service, notification-service |
| `tracking.events` | tracking-service | shipment-service |
| `notifications.dlq` | notification-service | (manual replay) |

All topics: 3 partitions, replication-factor 1 (single-broker local), retention 7 days.  
Schema: Avro via `apache/avro` (schema registry: Apicurio, open source).

---

## Patterns

### Transactional Outbox
- Each service writes domain event to local `outbox_events` table in the same DB transaction as the domain change
- Outbox poller reads unpublished rows, publishes to Kafka, marks published
- No dual-write race: DB is source of truth

### Idempotent Consumers
- Each consumer stores `(topic, partition, offset)` in a `processed_events` table
- Skips if already processed
- Valkey also used for short-TTL dedup on notification-service (idempotency key = `event_id`)

### Dead Letter Queue
- notification-service: failed events → `notifications.dlq` topic
- DLQ viewer endpoint + replay API on notification-service

---

## API (gateway routes)

```
POST   /orders                        → order-service: create order
GET    /orders/{id}                   → order-service: get order
DELETE /orders/{id}                   → order-service: cancel order
GET    /shipments/{order_id}          → shipment-service: get shipment
GET    /tracking/{shipment_id}        → tracking-service: tracking timeline
POST   /tracking/ingest               → tracking-service: carrier webhook (HMAC verified)
GET    /notifications/history/{cust}  → notification-service: sent notifications
POST   /notifications/dlq/{id}/replay → notification-service: replay DLQ event
```

---

## Observability

| Concern | Tool |
|---|---|
| Metrics | Micrometer → Prometheus |
| Tracing | OpenTelemetry SDK → Jaeger |
| Logging | Logback JSON → stdout |
| Health | Spring Boot Actuator `/actuator/health` |
| Load test | k6 (scripts in `k6/`) |

Jaeger all-in-one + Prometheus + Grafana run in docker-compose.  
Trace context propagated via W3C TraceContext headers across services.

---

## Tech Stack

| Concern | Choice |
|---|---|
| Language | Java 25 |
| Framework | Spring Boot 4 / Spring Cloud 2025 |
| Messaging | Apache Kafka KRaft 3.9+ |
| Cache | Valkey 8 |
| DB | PostgreSQL 17 (each service owns its schema) |
| Schema registry | Apicurio Registry 3 |
| Gateway | Spring Cloud Gateway |
| OTel | opentelemetry-java-agent 2.x |
| Tracing UI | Jaeger 2 all-in-one |
| Metrics | Prometheus 3 + Grafana 11 |
| Load test | k6 |
| Containers | Docker Compose (dev) + kind (k8s manifests) |
| CI | GitHub Actions (free) |
| Coverage | JaCoCo → badge on `badges` branch + shields.io |
| Build | Maven wrapper |

---

## Project Layout

```
shipstream/
├── api-gateway/
├── order-service/
├── shipment-service/
├── tracking-service/
├── notification-service/
├── payment-service/          # P7 optional
├── common/                   # shared Avro schemas, DTOs
├── docker-compose.yml        # full local stack
├── k8s/                      # kind manifests
├── k6/                       # load test scripts
├── scripts/                  # dev helpers
└── .github/workflows/ci.yml
```

Each service: standard Spring Boot Maven layout with `src/main/java`, `src/test/java`, `src/main/resources`.

---

## CI Pipeline

1. `mvn verify` (all services, parallel matrix)
2. JaCoCo coverage check (≥ 70% line)
3. Spotbugs + Checkstyle
4. Integration tests with Testcontainers (Kafka, Postgres, Valkey)
5. e2e smoke: docker-compose up → k6 smoke script
6. Upload JaCoCo XML → update coverage badge on `badges` branch

---

## Phases

| Phase | Deliverable | PR |
|---|---|---|
| P1 | Repo scaffold + common schemas + docker-compose infra | PR#1 |
| P2 | order-service (CRUD + outbox + Kafka publish) | PR#2 |
| P3 | shipment-service (consume orders.created + outbox) | PR#3 |
| P4 | tracking-service (ingest events + timeline API) | PR#4 |
| P5 | notification-service (idempotent + DLQ) | PR#5 |
| P6 | api-gateway + Spring Cloud Gateway routes + rate-limit | PR#6 |
| P7 | walletd payment saga (optional) | PR#7 |
| P8 | OTel + Jaeger + Prometheus + Grafana dashboard | PR#8 |
| P9 | k6 load tests + kind manifests | PR#9 |
| P10 | JaCoCo coverage + badges + README polish | PR#10 |

---

## Non-Goals (v1)

- No production Kubernetes (kind only)
- No Confluent / Redpanda / Redis (must stay OSS)
- No real email/SMS (stubs only)
- No OAuth2 / JWT auth on gateway (header passthrough only)
- No multi-region

---

## Success Criteria

- Order created → notification sent end-to-end via Kafka, traced in Jaeger
- Outbox survives service restart: no lost events
- Idempotency: duplicate Kafka delivery does not create duplicate notification
- k6 smoke: 100 RPS for 60s, p99 < 500ms, 0 errors
- CI green on every PR
- All 10 phases merged, README with architecture diagram + badges
