#!/usr/bin/env bash

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=scripts/e2e/common.sh
source "$SCRIPT_DIR/common.sh"

require_command docker
require_command curl
require_command jq
require_command stripe
require_env STRIPE_SECRET_KEY
require_payment_env_file

wait_for_gateway
verify_seed_data
assert_clean_stock
get_clean_cart "$CUSTOMER_C_ID"
pass "Cart ready"
add_product_to_cart "$CART_ID" 2
start_checkout "$CART_ID"
wait_for_order_created
wait_for_payment_awaiting

wait_for_reservation_status RESERVED
wait_for_stock '100|2|true'
wait_for_provider_payment_id
expire_stripe_session "$PROVIDER_PAYMENT_ID"

wait_for_payment_status FAILED
wait_for_reservation_status RELEASED
wait_for_order_payment_expired
wait_for_cart_status "$CART_ID" ACTIVE
wait_for_saga_state FAILED PAYMENT_EXPIRED
wait_for_stock '100|0|true'

printf '\nPAYMENT EXPIRY E2E: PASS\n'
