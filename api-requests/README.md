# API request smoke flows

## Prerequisites

- Docker with Compose.
- A local Stripe test account and Stripe CLI for payment flows.
- `payment-service/.env.local` containing runtime-only `STRIPE_SECRET_KEY` and `STRIPE_WEBHOOK_SECRET`. No secret belongs in this directory.
- IntelliJ HTTP Client (the files use its response handlers and `http-client.env.json`).

## Start

From the repository root:

```bash
docker compose up -d --build
```

Wait until `docker compose ps` shows the application containers as running. The gateway is `http://localhost:4002`. The compose `stock-service` uses the `dev` profile to load the idempotent stock prerequisite SQL. For a clean repeatable run:

```bash
docker compose down -v
docker compose up -d --build
```

The volume reset is development-only and removes the local PostgreSQL data volumes.

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
| Customer A | `c0000000-0000-0000-0000-000000000001` / `customer@example.com` | active, happy path |
| Customer B | `c0000000-0000-0000-0000-000000000002` / `out-of-stock@example.com` | active, out-of-stock flow |
| Customer C | `c0000000-0000-0000-0000-000000000003` / `payment-expiry@example.com` | active, expiry flow |
| Product A | `d0000000-0000-0000-0000-000000000001` | Mechanical Keyboard, TRY 2499.90, stock 100 |
| Product B | `d0000000-0000-0000-0000-000000000002` | Wireless Mouse, TRY 1299.90, stock 50 |
| Product C | `d0000000-0000-0000-0000-000000000003` | USB-C Dock, TRY 3499.90, stock 0 |

The repository has no authentication/login API or security configuration in this build, so the request flows are unauthenticated.

## Happy path

Run the files in this order:

1. `00-health.http`
2. `01-customer.http`
3. `02-products.http`
4. `03-cart.http`
5. `04-checkout.http`
6. Wait a few seconds for asynchronous payment creation.
7. `05-order-status.http`
8. `06-payment-checkpoint.http`
9. Open the captured `checkoutUrl` in a browser and complete Stripe Checkout manually.
10. `07-happy-path-verification.http`

Checkout immediately returns an `OrderResponse` and captures `orderId`. Stock reservation, payment creation, and later order/cart transitions are asynchronous; re-run status requests after Kafka has processed the events.

To expose the webhook locally, start this before paying:

```bash
stripe listen --forward-to http://localhost:4002/payments/webhooks/stripe
```

Copy the `whsec_...` printed by Stripe CLI into the local, uncommitted `payment-service/.env.local` as `STRIPE_WEBHOOK_SECRET`, then restart `payment-service`. Use Stripe test card `4242 4242 4242 4242`, any future expiry, and any CVC.

After checkout, wait a few seconds and run `06-payment-checkpoint.http`. It calls `GET {{baseUrl}}/payments/order/{{orderId}}` and captures the persisted `checkoutUrl`. An initial `404` is a normal asynchronous timing result; retry the request shortly afterwards. Open the returned URL in a browser and use Stripe test card `4242 4242 4242 4242`.

## Out-of-stock flow

Run `08-out-of-stock-flow.http` after steps 0-2. After Kafka processing, expect `OrderStatus.REJECTED` with `OUT_OF_STOCK` and `CartStatus.ACTIVE` (cart reopened). No Stripe payment should be created for this order.

## Payment expiry flow

Run `09-payment-expiry-flow.http`, wait for its payment checkpoint to return `AWAITING_CUSTOMER_ACTION`, then query the resulting `provider_payment_id` through the documented development-only DB path and expire the still-open Checkout Session using Stripe’s supported API operation:

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
