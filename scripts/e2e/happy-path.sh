#!/usr/bin/env bash

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=scripts/e2e/common.sh
source "$SCRIPT_DIR/common.sh"

require_command docker
require_command curl
require_command jq
require_command stripe
require_command open
require_payment_env_file

wait_for_gateway
verify_seed_data
assert_clean_stock
get_clean_cart "$CUSTOMER_A_ID"
pass "Cart ready"
add_product_to_cart "$CART_ID" 2

http_get "$BASE_URL/carts/$CART_ID"
[[ "$LAST_STATUS" == "200" ]] || fail "Could not verify cart after adding Product A"
[[ "$(json_value '.cartItems[0].quantity')" == "2" ]] || fail "Cart quantity verification failed"
pass "Cart quantity=2 verified"

start_checkout "$CART_ID"
wait_for_order_created
wait_for_payment_awaiting

if ! open "$CHECKOUT_URL" >/dev/null 2>&1; then
    fail "Could not open Stripe Checkout with macOS open"
fi
info "Stripe Checkout opened"
info "Use Stripe test card: 4242 4242 4242 4242"
info "Complete the payment in the browser, then press ENTER."
read -r

wait_for_payment_status COMPLETED
wait_for_reservation_status CONFIRMED
wait_for_order_status CONFIRMED
wait_for_cart_status "$CART_ID" COMPLETED
wait_for_saga_state COMPLETED
wait_for_stock '98|0|true'

printf '\nHAPPY PATH E2E: PASS\n'
