# Event-Driven E-Commerce Backend

Java 21 / Spring Boot 4.1.1 tabanlı, Kafka ile haberleşen event-driven bir e-commerce backend. Her business service kendi PostgreSQL veritabanına sahiptir.

Projenin odağı; transactional Outbox/Inbox, idempotency, optimistic locking, Saga orchestration, Stripe Checkout ve compensation tabanlı failure handling'dir. Core commerce workflow gerçek Stripe Checkout ile local ortamda uçtan uca doğrulanmıştır.

## Business Flow

```text
Cart → Checkout → Order Created → Stock Reserved
     → Payment Awaiting Customer → Payment Completed
     → Stock Confirmed → Order Confirmed → Cart Completed
```

Payment expiry compensation:

```text
Stripe Checkout expires
  → Payment FAILED → Reservation RELEASED
  → Order REJECTED → Cart ACTIVE → Saga FAILED
```

Business reason: `PAYMENT_EXPIRED`.

## Services

| Service | Responsibility | Port |
| --- | --- | ---: |
| API Gateway | External HTTP entry point and routing | 4002 |
| Customer Service | Customer and address lifecycle | 4001 |
| Product Service | Product catalog | 4003 |
| Order Service | Cart, checkout, order lifecycle | 4000 |
| Order Workflow Service | Order Saga orchestration and compensation | 4004* |
| Stock Service | Inventory and reservations | 4006 |
| Payment Service | Stripe Checkout and webhooks | 4007 |

`*` Order Workflow Service port is internal to Compose. Kafka is available on `9092`; Kafka UI on `4005`.

## Reliability and Consistency

- Database-per-service with Flyway-owned schemas.
- Transactional Outbox and consumer Inbox for reliable, idempotent messaging.
- Kafka consumers designed for at-least-once delivery.
- Saga orchestration with compensation instead of distributed rollback.
- Optimistic locking for carts, stock, reservations, and Saga state.
- Stock validates all requested items before mutating inventory.
- Stripe Checkout creation uses an order-based idempotency key.
- Stripe webhooks verify the `Stripe-Signature` header.

## Technology

Java 21 · Spring Boot 4.1.1 · Spring Cloud Gateway WebMVC · Spring Kafka · Spring Data JPA/Hibernate · PostgreSQL 16 · Flyway · Apache Kafka 4.1.1 · Stripe Java SDK 33.4.0 · Docker Compose · Testcontainers 2.0.5 · JUnit · Maven

## Run Locally

Prerequisites: Docker Compose, Maven, `make`, `curl`, `jq`, Stripe CLI, and macOS `open` for the happy-path browser step.

Create the uncommitted `payment-service/.env.local` file:

```dotenv
STRIPE_SECRET_KEY=sk_test_<your-test-key>
STRIPE_WEBHOOK_SECRET=whsec_<listener-secret>
```

Never commit real credentials. Start the stack directly with:

```bash
docker compose up -d --build
```

The Gateway is available at `http://localhost:4002`. Common routes are:

```text
GET  /products
GET  /customers/{customerId}
GET  /carts/current?customerId=...
POST /carts/{cartId}/checkout
GET  /orders/{orderId}
GET  /payments/order/{orderId}
```

Authentication is not implemented in the current local build.

## Stripe Local Webhook

Run this in a separate terminal:

```bash
make stripe-listen
```

It executes:

```bash
stripe listen --latest --forward-to http://localhost:4002/payments/webhooks/stripe
```

Copy the printed `whsec_...` into `payment-service/.env.local` as `STRIPE_WEBHOOK_SECRET`, then restart the Payment Service or run `make e2e-reset`. The listener has been verified with API version `2026-08-26.dahlia`, matching the current Stripe SDK integration.

## End-to-End Testing

The scripts call the Gateway, poll asynchronous Kafka-driven state, and use read-only development DB queries only for internal states that have no public endpoint. They do not expose `provider_payment_id` through production APIs.

### Reset

```bash
make e2e-reset
```

This removes local Compose volumes, rebuilds services, runs Flyway migrations, verifies deterministic seed data, and waits for readiness. Use it before a new scenario or after a dirty/partial flow.

### Happy path

With the Stripe listener running:

```bash
make e2e-happy
```

The script creates the seeded cart and checkout, waits for the payment and Checkout URL, opens the hosted Stripe page, and verifies the final states. The only manual step is completing payment with test card `4242 4242 4242 4242`, any future expiry, and any CVC; then press ENTER.

Expected result for Product A quantity `2`:

```text
Payment       COMPLETED
Reservation   CONFIRMED
Order         CONFIRMED
Cart          COMPLETED
Saga          COMPLETED
Stock         onHand=98 reserved=0
```

### Payment expiry

This flow is fully automated:

```bash
make e2e-reset
export STRIPE_SECRET_KEY=sk_test_<your-test-key>
make e2e-expiry
```

It creates a real Checkout Session, waits for reservation, reads the real provider session ID from the local Payment DB, expires that exact Stripe Session, waits for the signed webhook, and verifies compensation.

Expected result:

```text
Payment       FAILED
Reservation   RELEASED
Order         REJECTED / PAYMENT_EXPIRED
Cart          ACTIVE
Saga          FAILED
Stock         onHand=100 reserved=0
```

Both scripts use bounded polling, report the last observed state on timeout, and reject dirty seeded state.

## Manual API Requests

[`api-requests/`](api-requests/) contains IntelliJ HTTP Client flows for manual inspection and debugging. [`scripts/e2e/`](scripts/e2e/) contains repeatable full-flow verification. The manual collection has its own [README](api-requests/README.md).

## Testing

The repository includes:

- Domain and web-layer tests
- PostgreSQL/Testcontainers integration tests
- Flyway validation
- Kafka contract and routing tests
- Inbox/Outbox and idempotency tests
- Optimistic-locking and rollback tests
- Saga compensation tests
- Stripe webhook signature tests
- Real Stripe E2E flows

Run the multi-module suite with Docker available for Testcontainers:

```bash
mvn test
```

An aggregate test count is intentionally not pinned here because the full suite was not re-run after the latest signed webhook additions.

## Current State

Implemented and locally verified: cart/checkout, order lifecycle, stock reservation and release, real Stripe Checkout, signed webhook processing, payment-expiry compensation, cart reopening, Saga completion/failure, and repeatable E2E tooling.

This is a development/portfolio project, not a production-scale performance claim.

## Next Steps

Not implemented yet:

- Gateway authentication/authorization
- Rate limiting
- Actuator, Micrometer, Prometheus, and Grafana
- k6 load testing and p95/p99 characterization
- Multi-instance Outbox publisher claiming/hardening
- Optional notification service

