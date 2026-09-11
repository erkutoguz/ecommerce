# Event-Driven E-Commerce Backend

A backend-focused e-commerce platform built with Java, Spring Boot, PostgreSQL, Kafka and Stripe.

The project started as a way to build a complete checkout flow, but gradually evolved into an exercise in distributed systems: handling asynchronous workflows, duplicate messages, concurrent stock updates, payment failures and compensation without relying on distributed transactions.

The current focus is backend correctness and reliability rather than frontend implementation.

## What it does

The platform currently supports the core e-commerce flow from cart to confirmed order:

```text
Cart
  ↓
Checkout
  ↓
Order Created
  ↓
Stock Reserved
  ↓
Payment
  ↓
Stock Confirmed
  ↓
Order Confirmed
  ↓
Cart Completed
```

The workflow is coordinated through a dedicated Saga / Process Manager rather than tightly coupling the services together.

Payment failures are also handled as part of the workflow.

For example, when a Stripe Checkout Session expires:

```text
Payment Failed
  ↓
Release Reserved Stock
  ↓
Reject Order
  ↓
Reopen Cart
```

The order is not rejected before the stock compensation succeeds, which keeps the workflow state explicit and avoids leaving the system in an inconsistent intermediate state.

## Services

The system is split into independent services with their own responsibilities and databases.

- **API Gateway** — external entry point and service routing
- **Customer Service** — customer-related operations
- **Product Service** — product catalog
- **Order Service** — cart, checkout and order lifecycle
- **Stock Service** — inventory and stock reservations
- **Payment Service** — Stripe Checkout integration and payment state
- **Order Workflow Service** — Saga orchestration across order, stock and payment

Services do not share databases.

Synchronous HTTP is used where an immediate response is required, while Kafka is used for asynchronous workflow transitions.

## Reliability

A large part of the project is focused on what happens when the happy path does not behave perfectly.

The system currently includes:

- Transactional Outbox for reliable event publishing
- Inbox-based idempotency for message consumers
- At-least-once message delivery handling
- Saga-based compensation
- Optimistic locking for concurrent stock operations
- Duplicate event protection
- Stale and out-of-order event handling
- Atomic stock reservation, confirmation and release
- Stripe webhook signature verification and idempotency
- Flyway-managed database migrations

Stock operations follow explicit quantity invariants.

For a stock item:

```text
available = onHand - reserved
```

Reservation:

```text
reserved += quantity
```

Confirmation:

```text
onHand   -= quantity
reserved -= quantity
```

Release:

```text
reserved -= quantity
```

This allows payment and stock failures to be compensated without manually repairing inventory state.

## Technology

The main stack currently includes:

- Java 21
- Spring Boot 4
- Spring Cloud Gateway
- Spring Data JPA / Hibernate
- PostgreSQL
- Apache Kafka
- Flyway
- Stripe
- Docker / Docker Compose
- Testcontainers
- JUnit

## Testing

The project currently has **428 automated tests** across the main checkout services.

```text
Stock Service       79
Payment Service     50
Workflow Service    96
Order Service      203
-----------------------
Total               428
```

The test suite covers more than individual service methods.

It includes scenarios such as:

- real PostgreSQL integration tests
- transaction rollback
- duplicate Kafka messages
- Stripe webhook duplication
- stale payment events
- stock reservation failure
- payment-expiry compensation
- order rejection and cart reopening
- concurrent reservation confirmation vs. release
- optimistic locking
- Flyway and Hibernate schema validation

One of the concurrency tests intentionally races stock confirmation and stock release against the same reservation and verifies that only one transaction can succeed.

## Current State

The main checkout workflow is complete.

The following flows are currently implemented and tested:

```text
Checkout
→ Stock Reservation
→ Payment
→ Stock Confirmation
→ Order Confirmation
```

and:

```text
Payment Expiration
→ Payment Failure
→ Stock Release
→ Order Rejection
→ Cart Reopen
```

The project is now moving from core business implementation into infrastructure and operational hardening.

## Next

The next milestones are focused on making the system easier to operate and evaluate under realistic conditions:

- authentication and authorization at the API Gateway boundary
- gateway rate limiting
- end-to-end API request collection with deterministic development data
- Spring Boot Actuator and application metrics
- Prometheus and Grafana monitoring
- k6 load and stress testing
- p95 / p99 latency measurements
- concurrent checkout and overselling benchmarks
- improved architecture and operational documentation

A notification service is also planned, but it will remain outside the core Saga so that notification failures cannot roll back a completed order.

## Running and Testing Locally

The complete development environment can be started with Docker Compose:

```bash
docker compose up -d --build
```

The public API is exposed through the API Gateway:

```text
http://localhost:4002
```

Development data is seeded with deterministic customers, products and stock so the checkout flows can be reproduced without manually preparing each service database.

Runtime entities such as carts, orders, stock reservations, payments and Saga instances are not seeded. They are created through the actual application flow.

### Manual API flow

The `api-requests/` directory contains an ordered set of IntelliJ HTTP Client requests for exercising the system.

For the normal checkout flow, run:

```text
00-health.http
01-customer.http
02-products.http
03-cart.http
04-checkout.http
05-order-status.http
06-payment-checkpoint.http
07-happy-path-verification.http
```

The flow uses the seeded customer and an in-stock product:

```text
Customer
→ Cart
→ Add Product
→ Checkout
→ Stock Reservation
→ Payment
→ Stock Confirmation
→ Order Confirmation
→ Cart Completion
```

Because the workflow is asynchronous, Kafka processing may take a few seconds between checkout and the following verification requests.

### Stripe

Stripe Checkout is used in test mode.

To receive Stripe webhooks locally, start the Stripe CLI:

```bash
stripe listen --forward-to http://localhost:4002/payments/webhooks/stripe
```

The webhook signing secret printed by the CLI should be provided through the local Payment Service environment configuration.

No Stripe secrets are stored in the repository.

After checkout, the Payment Service creates a real Stripe Checkout Session. The generated Checkout URL can then be opened and completed using Stripe's test card:

```text
4242 4242 4242 4242
```

After the webhook is processed, the expected final state is:

```text
Payment       COMPLETED
Reservation   CONFIRMED
Order         CONFIRMED
Cart          COMPLETED
```

For the seeded happy-path product, an order with quantity `2` results in:

```text
Before checkout:
onHand   = 100
reserved = 0

During payment:
onHand   = 100
reserved = 2

After confirmation:
onHand   = 98
reserved = 0
```

### Out-of-stock flow

An additional seeded product has zero available stock.

The failure scenario can be exercised with:

```text
08-out-of-stock-flow.http
```

Expected result:

```text
Stock reservation fails
→ Order REJECTED
→ Cart ACTIVE
→ No Payment created
```

The stock quantities remain unchanged.

### Payment-expiry compensation

A separate customer is available for testing the payment-expiry compensation path:

```text
09-payment-expiry-flow.http
```

After checkout, the system reaches:

```text
Payment       AWAITING_CUSTOMER_ACTION
Reservation   RESERVED
```

The corresponding Stripe Checkout Session can then be expired using Stripe's API.

Once the signed Stripe webhook reaches the Payment Service, the system continues asynchronously:

```text
checkout.session.expired
→ Payment FAILED
→ Release Stock Reservation
→ Order REJECTED
→ Cart ACTIVE
→ Saga FAILED
```

Stock is restored without consuming inventory:

```text
Before checkout:
onHand   = 100
reserved = 0

During payment:
onHand   = 100
reserved = 2

After compensation:
onHand   = 100
reserved = 0
```

### Current development data

The local development environment includes:

| Data | Purpose |
| --- | --- |
| `customer@example.com` | normal checkout |
| `out-of-stock@example.com` | stock failure scenario |
| `payment-expiry@example.com` | payment compensation scenario |
| Mechanical Keyboard | in-stock product |
| Wireless Mouse | additional in-stock product |
| USB-C Dock | out-of-stock product |

The IDs are deterministic and are documented in `api-requests/README.md`.

### Current API limitations

The project intentionally does not expose debugging endpoints solely for testing.

At the moment there are no public read endpoints for Payment, Stock or Saga state. Internal states that cannot be observed through the public API can be inspected through the local development database or service logs.

Authentication, gateway rate limiting, observability and performance testing are part of the next development milestone.

For the complete manual test sequence and development data, see:

```text
api-requests/README.md
```

## Project Direction

The purpose of this project is not to reproduce every feature of a commercial e-commerce platform.

The focus is on the backend problems that become important once multiple services participate in the same business process:

- who owns each piece of data
- how services communicate without sharing transactions
- what happens when a message is delivered twice
- how failures are compensated
- how concurrent updates are kept consistent
- how external payment providers are integrated safely
- how the system can be tested beyond the happy path

The remaining work is primarily around security, observability, performance testing and documentation rather than the core checkout model.
