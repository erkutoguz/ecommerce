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

## Running Locally

The project is designed to run locally with Docker-based infrastructure.

```bash
docker compose up -d
```

Individual services can also be started separately during development.

Stripe integration uses Stripe test mode. Local webhook development requires a valid Stripe test configuration and webhook forwarding.

More detailed setup instructions and reproducible API flows are being added as part of the current development milestone.

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
