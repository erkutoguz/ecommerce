# API request smoke flows

## Prerequisites

- Docker with Compose.
- Local RSA files at `~/.ecommerce-keys/private.pem` and `~/.ecommerce-keys/public.pem` for Auth Service signing and Gateway verification. The Gateway must receive only the public key.
- A local Stripe test account and Stripe CLI for payment flows.
- `payment-service/.env.local` containing runtime-only `STRIPE_SECRET_KEY` and `STRIPE_WEBHOOK_SECRET`. No secret belongs in this directory.
- IntelliJ HTTP Client (the files use its response handlers and `http-client.env.json`).

Select the `local` JetBrains HTTP Client environment before running requests. Its static configuration comes from `api-requests/http-client.env.json`; do not rely on ignored IDE workspace state. Runtime values such as tokens and resource IDs are captured by the request handlers.

## Start

From the repository root:

```bash
docker compose up -d --build
```

Compose waits for Kafka readiness and the one-shot `kafka-init` service before starting Kafka-dependent applications. `kafka-init` creates and verifies the application topics; application and broker topic auto-creation are disabled. Wait until `docker compose ps` shows `kafka-init` exited with code 0 and the application containers as running. The gateway is `http://localhost:4002`. The compose `stock-service` uses the `dev` profile to load the idempotent stock prerequisite SQL. For a clean repeatable run:

```bash
docker compose down -v
docker compose up -d --build
```

The volume reset is development-only and removes the local PostgreSQL data volumes.

## Execution model

`Auth.http` is the bootstrap. It creates a fresh user, logs in, and must be repeated until Customer provisioning is visible. The standard happy path is then run through `00-health.http` and `01`–`07`; `01-customer.http` is optional inspection and is not a prerequisite for Cart or Order security.

`08-out-of-stock-flow.http`, `09-payment-expiry-flow.http`, and `10-ownership-flow.http` are independent scenarios. Each starts by clearing its own runtime IDs, but each requires the authenticated bootstrap state described in its file. They are not continuation steps after the happy path.

## Full end-to-end verification

For the repeatable full flows, run from the repository root:

```bash
make e2e-happy
make e2e-expiry
```

The manual `.http` files remain useful for inspecting individual steps and debugging.

The Stripe listener runs in a separate terminal:

```bash
make stripe-listen
```

This repository uses the installed Stripe CLI's `--latest` option so forwarded
events match the Stripe Java SDK API version. Copy the listener's printed
`whsec_...` into the uncommitted `payment-service/.env.local`, restart the
payment service, and then run the E2E command. Never commit either secret.

## Seeded data

| Entity | ID / email | Important values |
| --- | --- | --- |
| Product A | `d0000000-0000-0000-0000-000000000001` | Mechanical Keyboard, TRY 2499.90, stock 100 |
| Product B | `d0000000-0000-0000-0000-000000000002` | Wireless Mouse, TRY 1299.90, stock 50 |
| Product C | `d0000000-0000-0000-0000-000000000003` | USB-C Dock, TRY 3499.90, stock 0 |

## Authentication

Run `Auth.http` to register and log in through the Gateway. Registration provisions a Customer asynchronously through Kafka. Repeat the authenticated Customer lookup in `Auth.http` until the registered email appears. The captured `customerId` is useful for inspecting `01-customer.http`; authenticated Cart and Order requests derive Customer identity from the JWT and do not send `customerId` query parameters. The Gateway validates the token and enforces the configured USER/ADMIN route policy.

## Happy path

Run the files in this order:

1. `00-health.http`
2. `Auth.http` (register, login, and poll Customer provisioning)
3. `01-customer.http` (optional Customer inspection)
4. `02-products.http`
5. `03-cart.http`
6. `04-checkout.http`
7. Wait a few seconds for asynchronous payment creation.
8. `05-order-status.http`
9. `06-payment-checkpoint.http`
10. Open the captured `checkoutUrl` in a browser and complete Stripe Checkout manually.
11. `07-happy-path-verification.http`

## Ownership smoke flow

Run `10-ownership-flow.http` after the stack is ready. It registers and logs in two temporary users, waits for User A's Customer provisioning, obtains User A's current Cart, and verifies that User B receives `404` when requesting that Cart with User B's JWT. The flow does not call the Customer Service internal endpoint through the Gateway.

Checkout immediately returns an `OrderResponse` and captures `orderId`. Stock reservation, payment creation, and later order/cart transitions are asynchronous; re-run status requests after Kafka has processed the events.

To expose the webhook locally, start this before paying:

```bash
stripe listen --forward-to http://localhost:4002/payments/webhooks/stripe
```

Copy the `whsec_...` printed by Stripe CLI into the local, uncommitted `payment-service/.env.local` as `STRIPE_WEBHOOK_SECRET`, then restart `payment-service`. Use Stripe test card `4242 4242 4242 4242`, any future expiry, and any CVC.

After checkout, wait a few seconds and run `06-payment-checkpoint.http`. It calls `GET {{baseUrl}}/payments/order/{{orderId}}` and captures the persisted `checkoutUrl`. An initial `404` is a normal asynchronous timing result; retry the request shortly afterwards. Open the returned URL in a browser and use Stripe test card `4242 4242 4242 4242`.

## Out-of-stock flow

Run `Auth.http` first and repeat its Customer provisioning request until the current user is visible. `01-customer.http` is optional inspection. Then run `08-out-of-stock-flow.http` independently. After Kafka processing, expect `OrderStatus.REJECTED` with `OUT_OF_STOCK` and `CartStatus.ACTIVE` (cart reopened). No Stripe payment should be created for this order. Saga `FAILED` is verified through existing development tooling because it is not exposed by the public API.

## Payment expiry flow

Run `Auth.http` first and repeat its Customer provisioning request until the current user is visible. `01-customer.http` is optional inspection. Then run `09-payment-expiry-flow.http` with the same one-unit Product A fixture used by the shell flow. Wait for its payment checkpoint to return `AWAITING_CUSTOMER_ACTION`, then query the resulting `provider_payment_id` through the documented development-only DB path and expire the still-open Checkout Session using Stripe’s supported API operation:

```bash
curl -X POST "https://api.stripe.com/v1/checkout/sessions/<provider_payment_id>/expire" \
  -u "${STRIPE_SECRET_KEY}:"
```

Load `STRIPE_SECRET_KEY` into the shell from the local uncommitted environment before running this command; never paste it into this directory.

Alternatively wait for the real session expiry. The application creates sessions with the production setting of 30 minutes. Do not send a fake webhook or hard-code a Stripe signature. After the signed `checkout.session.expired` webhook and Kafka processing, expect payment `FAILED`, order `REJECTED` with `PAYMENT_EXPIRED`, cart `ACTIVE`, and released stock.

## Internal verification and limitations

The payment checkpoint is public through the gateway, but the provider session id remains internal and is only needed by the expiry test. There is no public stock read controller or saga query endpoint. Development-only DB checks are therefore:

```bash
docker compose exec payment-db psql -U payment -d payment -c "select order_id, status, provider_payment_id from payments where order_id = '<order-id>';"
docker compose exec stock-db psql -U stock -d stock -c "select product_id, on_hand_quantity, reserved_quantity, active from stock_items order by product_id;"
docker compose exec order-workflow-db psql -U order-workflow -d order-workflow -c "select * from order_sagas where order_id = '<order-id>';"
```

The exact saga table columns are owned by the existing migration; use `\d order_sagas` in `psql` if a column-specific query is needed. Cart/order state is verified through the gateway request files.
