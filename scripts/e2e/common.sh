#!/usr/bin/env bash

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
BASE_URL="${E2E_BASE_URL:-http://localhost:4002}"
POLL_INTERVAL_SECONDS="${E2E_POLL_INTERVAL_SECONDS:-1}"
POLL_TIMEOUT_SECONDS="${E2E_POLL_TIMEOUT_SECONDS:-60}"

PRODUCT_A_ID="d0000000-0000-0000-0000-000000000001"
PRODUCT_B_ID="d0000000-0000-0000-0000-000000000002"
PRODUCT_C_ID="d0000000-0000-0000-0000-000000000003"

LAST_STATUS=""
LAST_BODY=""
POLL_LAST_OBSERVED=""

info() {
    printf '[INFO] %s\n' "$*"
}

pass() {
    printf '[PASS] %s\n' "$*"
}

fail() {
    printf '[FAIL] %s\n' "$*" >&2
    exit 1
}

require_command() {
    command -v "$1" >/dev/null 2>&1 || fail "$1 is required"
}

require_env() {
    [[ -n "${!1:-}" ]] || fail "$1 is required"
}

require_payment_env_file() {
    local env_file="$ROOT_DIR/payment-service/.env.local"
    [[ -f "$env_file" ]] || fail "$env_file is required by docker compose; create it locally without committing secrets"
    grep -q '^STRIPE_SECRET_KEY=' "$env_file" || fail "STRIPE_SECRET_KEY is missing from payment-service/.env.local"
    grep -q '^STRIPE_WEBHOOK_SECRET=' "$env_file" || fail "STRIPE_WEBHOOK_SECRET is missing from payment-service/.env.local"
}

compose() {
    docker compose -f "$ROOT_DIR/docker-compose.yaml" "$@"
}

http_request_with_token() {
    local token="$1"
    shift
    local method="$1"
    local url="$2"
    local body="${3:-}"
    local response_file
    local curl_status
    local -a curl_args

    response_file="$(mktemp "${TMPDIR:-/tmp}/e2e-http.XXXXXX")"
    curl_args=(
        --silent --show-error --connect-timeout 3 --max-time 15
        -o "$response_file" -w '%{http_code}'
    )

    if [[ -n "$token" ]]; then
        curl_args+=( -H "Authorization: Bearer $token" )
    fi

    if [[ "$method" == "GET" ]]; then
        if ! curl_status="$(curl "${curl_args[@]}" "$url")"; then
            LAST_STATUS="000"
            LAST_BODY=""
            rm -f "$response_file"
            return 0
        fi
    else
        curl_args+=( -X "$method" -H 'Content-Type: application/json' --data "$body" )
        if ! curl_status="$(curl "${curl_args[@]}" "$url")"; then
            LAST_STATUS="000"
            LAST_BODY=""
            rm -f "$response_file"
            return 0
        fi
    fi

    LAST_STATUS="$curl_status"
    LAST_BODY="$(<"$response_file")"
    rm -f "$response_file"
}

http_request() {
    http_request_with_token "" "$@"
}

http_get() {
    http_request GET "$1"
}

http_get_auth() {
    [[ -n "${E2E_ACCESS_TOKEN:-}" ]] || fail "E2E access token is not available"
    http_request_with_token "$E2E_ACCESS_TOKEN" GET "$1"
}

http_post_json() {
    http_request POST "$1" "$2"
}

http_post_json_auth() {
    [[ -n "${E2E_ACCESS_TOKEN:-}" ]] || fail "E2E access token is not available"
    http_request_with_token "$E2E_ACCESS_TOKEN" POST "$1" "$2"
}

json_value() {
    local expression="$1"
    jq -r "$expression" <<<"$LAST_BODY"
}

valid_uuid() {
    [[ "$1" =~ ^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$ ]]
}

poll_until() {
    local description="$1"
    local timeout_seconds="$2"
    shift 2
    local deadline=$((SECONDS + timeout_seconds))

    while (( SECONDS <= deadline )); do
        if "$@"; then
            return 0
        fi
        sleep "$POLL_INTERVAL_SECONDS"
    done

    if [[ -n "$POLL_LAST_OBSERVED" ]]; then
        fail "Timed out waiting for $description; last observed state: $POLL_LAST_OBSERVED"
    fi
    fail "Timed out waiting for $description"
}

gateway_ready() {
    http_get "$BASE_URL/products/$PRODUCT_A_ID"
    if [[ "$LAST_STATUS" != "200" ]] || ! jq -e --arg id "$PRODUCT_A_ID" '.productId == $id' <<<"$LAST_BODY" >/dev/null 2>&1; then
        POLL_LAST_OBSERVED="Product route HTTP $LAST_STATUS"
        return 1
    fi
    return 0
}

wait_for_gateway() {
    poll_until "Gateway reachable" 120 gateway_ready
    pass "Gateway reachable"
}

query_payment_db() {
    local order_id="$1"
    valid_uuid "$order_id" || fail "Invalid order id for payment DB query"
    compose exec -T payment-db psql -X -qAt -U payment -d payment \
        -c "SELECT status || '|' || COALESCE(provider_payment_id, '') FROM payments WHERE order_id = '$order_id';"
}

query_stock_db() {
    local product_id="$1"
    valid_uuid "$product_id" || fail "Invalid product id for stock DB query"
    compose exec -T stock-db psql -X -qAt -U stock -d stock \
        -c "SELECT on_hand_quantity || '|' || reserved_quantity || '|' || active FROM stock_items WHERE product_id = '$product_id';"
}

query_reservation_db() {
    local order_id="$1"
    valid_uuid "$order_id" || fail "Invalid order id for reservation DB query"
    compose exec -T stock-db psql -X -qAt -U stock -d stock \
        -c "SELECT status FROM reservations WHERE order_id = '$order_id';"
}

query_workflow_db() {
    local order_id="$1"
    valid_uuid "$order_id" || fail "Invalid order id for workflow DB query"
    compose exec -T order-workflow-db psql -X -qAt -U order-workflow -d order-workflow \
        -c "SELECT state || '|' || COALESCE(failure_reason, '') FROM order_sagas WHERE order_id = '$order_id';"
}

verify_seed_data() {
    local expected_product_id expected_product_name

    for expected_product_id in "$PRODUCT_A_ID" "$PRODUCT_B_ID" "$PRODUCT_C_ID"; do
        http_get "$BASE_URL/products/$expected_product_id"
        [[ "$LAST_STATUS" == "200" ]] || fail "Seeded product unavailable: $expected_product_id"
        [[ "$(json_value '.status')" == "ACTIVE" ]] || fail "Seeded product is not ACTIVE: $expected_product_id"
        case "$expected_product_id" in
            "$PRODUCT_A_ID") expected_product_name="Mechanical Keyboard" ;;
            "$PRODUCT_B_ID") expected_product_name="Wireless Mouse" ;;
            "$PRODUCT_C_ID") expected_product_name="USB-C Dock" ;;
        esac
        [[ "$(json_value '.name')" == "$expected_product_name" ]] || fail "Unexpected seeded product data: $expected_product_id"
    done
    pass "Products available"

    [[ "$(query_stock_db "$PRODUCT_A_ID")" == '100|0|true' ]] || fail "Product A seed stock must be onHand=100 reserved=0"
    [[ "$(query_stock_db "$PRODUCT_B_ID")" == '50|0|true' ]] || fail "Product B seed stock must be onHand=50 reserved=0"
    [[ "$(query_stock_db "$PRODUCT_C_ID")" == '0|0|true' ]] || fail "Product C seed stock must be onHand=0 reserved=0"
    pass "Seed stock verified"
}

prepare_e2e_identity() {
    if [[ -z "${E2E_EMAIL:-}" ]]; then
        E2E_EMAIL="e2e-$(date +%s)-$$-${RANDOM}@example.com"
    fi
    E2E_PASSWORD="${E2E_PASSWORD:-E2eTestPassword123!}"
}

register_e2e_user() {
    http_post_json "$BASE_URL/auth/register" \
        "{\"email\":\"$E2E_EMAIL\",\"password\":\"$E2E_PASSWORD\"}"
    case "$LAST_STATUS" in
        2??) pass "E2E user registered: $E2E_EMAIL" ;;
        *) fail "E2E registration failed (HTTP $LAST_STATUS)" ;;
    esac
}

login_e2e_user() {
    http_post_json "$BASE_URL/auth/login" \
        "{\"email\":\"$E2E_EMAIL\",\"password\":\"$E2E_PASSWORD\"}"
    [[ "$LAST_STATUS" == "200" ]] || fail "E2E login failed (HTTP $LAST_STATUS)"
    E2E_ACCESS_TOKEN="$(json_value '.accessToken // empty')"
    [[ -n "$E2E_ACCESS_TOKEN" && "$E2E_ACCESS_TOKEN" != "null" ]] || fail "E2E login response did not contain an access token"
    pass "E2E user logged in"
}

customer_is_provisioned() {
    http_get_auth "$BASE_URL/customers?page=0&size=100"
    if [[ "$LAST_STATUS" != "200" ]]; then
        POLL_LAST_OBSERVED="Customer list HTTP $LAST_STATUS"
        return 1
    fi

    E2E_CUSTOMER_ID="$(jq -r --arg email "$E2E_EMAIL" \
        '[.content[]? | select(.email == $email) | .customerId][0] // empty' <<<"$LAST_BODY")"
    if valid_uuid "$E2E_CUSTOMER_ID"; then
        return 0
    fi

    POLL_LAST_OBSERVED="Customer not provisioned yet"
    return 1
}

bootstrap_e2e_customer() {
    prepare_e2e_identity
    register_e2e_user
    login_e2e_user
    poll_until "Customer provisioning for $E2E_EMAIL" "$POLL_TIMEOUT_SECONDS" customer_is_provisioned
    pass "Customer provisioned: $E2E_CUSTOMER_ID"
}

assert_clean_stock() {
    [[ "$(query_stock_db "$PRODUCT_A_ID")" == '100|0|true' ]] || fail "E2E environment is not clean: Product A stock changed. Run: make e2e-reset"
}

get_clean_cart() {
    local customer_id="$1"
    http_get_auth "$BASE_URL/carts/current?customerId=$customer_id"
    [[ "$LAST_STATUS" == "200" ]] || fail "Could not get current cart for customer $customer_id (HTTP $LAST_STATUS)"
    [[ "$(json_value '.status')" == "ACTIVE" ]] || fail "E2E environment is not clean: cart is not ACTIVE. Run: make e2e-reset"
    [[ "$(json_value '.cartItems | length')" == "0" ]] || fail "E2E environment is not clean: cart contains items. Run: make e2e-reset"
    CART_ID="$(json_value '.id')"
    valid_uuid "$CART_ID" || fail "Current cart response did not contain a valid cart id"
}

add_product_to_cart() {
    local cart_id="$1"
    local quantity="$2"
    http_post_json_auth "$BASE_URL/carts/$cart_id/items" \
        "{\"productId\":\"$PRODUCT_A_ID\",\"quantity\":$quantity}"
    [[ "$LAST_STATUS" == "200" ]] || fail "Could not add Product A to cart (HTTP $LAST_STATUS)"
    [[ "$(json_value '.cartItems[0].productId')" == "$PRODUCT_A_ID" ]] || fail "Cart does not contain Product A"
    [[ "$(json_value '.cartItems[0].quantity')" == "$quantity" ]] || fail "Cart Product A quantity is not $quantity"
    pass "Product added"
}

start_checkout() {
    local cart_id="$1"
    http_post_json_auth "$BASE_URL/carts/$cart_id/checkout" '{"currency":"TRY"}'
    [[ "$LAST_STATUS" == "201" ]] || fail "Checkout did not start (HTTP $LAST_STATUS)"
    ORDER_ID="$(json_value '.orderId')"
    valid_uuid "$ORDER_ID" || fail "Checkout response did not contain a valid order id"
    pass "Checkout started"
    pass "Order created: $ORDER_ID"
}

order_is_checkout_progressing() {
    http_get_auth "$BASE_URL/orders/$ORDER_ID"
    if [[ "$LAST_STATUS" == "200" ]]; then
        ORDER_STATUS="$(json_value '.status')"
        ORDER_REJECTION_REASON="$(json_value '.rejectionReason')"
        POLL_LAST_OBSERVED="$ORDER_STATUS"
        case "$ORDER_STATUS" in
            PENDING_STOCK|PENDING_PAYMENT|PENDING_STOCK_CONFIRMATION|CONFIRMED) return 0 ;;
        esac
        return 1
    fi
    POLL_LAST_OBSERVED="HTTP $LAST_STATUS"
    return 1
}

wait_for_order_created() {
    poll_until "Order $ORDER_ID to enter checkout flow" "$POLL_TIMEOUT_SECONDS" order_is_checkout_progressing
}

payment_is_awaiting() {
    http_get_auth "$BASE_URL/payments/order/$ORDER_ID"
    if [[ "$LAST_STATUS" == "200" ]]; then
        PAYMENT_STATUS="$(json_value '.status')"
        CHECKOUT_URL="$(json_value '.checkoutUrl')"
        POLL_LAST_OBSERVED="$PAYMENT_STATUS"
        [[ "$PAYMENT_STATUS" == "AWAITING_CUSTOMER_ACTION" && "$CHECKOUT_URL" != "null" && -n "$CHECKOUT_URL" ]]
        return
    fi
    POLL_LAST_OBSERVED="HTTP $LAST_STATUS"
    return 1
}

payment_is_status() {
    local expected_status="$1"
    http_get_auth "$BASE_URL/payments/order/$ORDER_ID"
    if [[ "$LAST_STATUS" == "200" ]]; then
        PAYMENT_STATUS="$(json_value '.status')"
        POLL_LAST_OBSERVED="$PAYMENT_STATUS"
        [[ "$PAYMENT_STATUS" == "$expected_status" ]]
        return
    fi
    POLL_LAST_OBSERVED="HTTP $LAST_STATUS"
    return 1
}

wait_for_payment_awaiting() {
    poll_until "Payment AWAITING_CUSTOMER_ACTION" "$POLL_TIMEOUT_SECONDS" payment_is_awaiting
    pass "Payment AWAITING_CUSTOMER_ACTION"
}

wait_for_payment_status() {
    local expected_status="$1"
    poll_until "Payment $expected_status" "$POLL_TIMEOUT_SECONDS" payment_is_status "$expected_status"
    pass "Payment $expected_status"
}

order_is_status() {
    local expected_status="$1"
    http_get_auth "$BASE_URL/orders/$ORDER_ID"
    if [[ "$LAST_STATUS" == "200" ]]; then
        ORDER_STATUS="$(json_value '.status')"
        ORDER_REJECTION_REASON="$(json_value '.rejectionReason')"
        POLL_LAST_OBSERVED="$ORDER_STATUS${ORDER_REJECTION_REASON:+/$ORDER_REJECTION_REASON}"
        [[ "$ORDER_STATUS" == "$expected_status" ]]
        return
    fi
    POLL_LAST_OBSERVED="HTTP $LAST_STATUS"
    return 1
}

order_is_rejected_for_payment_expiry() {
    http_get_auth "$BASE_URL/orders/$ORDER_ID"
    if [[ "$LAST_STATUS" == "200" ]]; then
        ORDER_STATUS="$(json_value '.status')"
        ORDER_REJECTION_REASON="$(json_value '.rejectionReason')"
        POLL_LAST_OBSERVED="$ORDER_STATUS/$ORDER_REJECTION_REASON"
        [[ "$ORDER_STATUS" == "REJECTED" && "$ORDER_REJECTION_REASON" == "PAYMENT_EXPIRED" ]]
        return
    fi
    POLL_LAST_OBSERVED="HTTP $LAST_STATUS"
    return 1
}

wait_for_order_status() {
    local expected_status="$1"
    poll_until "Order $expected_status" "$POLL_TIMEOUT_SECONDS" order_is_status "$expected_status"
    pass "Order $expected_status"
}

wait_for_order_payment_expired() {
    poll_until "Order REJECTED/PAYMENT_EXPIRED" "$POLL_TIMEOUT_SECONDS" order_is_rejected_for_payment_expiry
    pass "Order REJECTED (PAYMENT_EXPIRED)"
}

cart_is_status() {
    local cart_id="$1"
    local expected_status="$2"
    http_get_auth "$BASE_URL/carts/$cart_id"
    if [[ "$LAST_STATUS" == "200" ]]; then
        CART_STATUS="$(json_value '.status')"
        POLL_LAST_OBSERVED="$CART_STATUS"
        [[ "$CART_STATUS" == "$expected_status" ]]
        return
    fi
    POLL_LAST_OBSERVED="HTTP $LAST_STATUS"
    return 1
}

wait_for_cart_status() {
    local cart_id="$1"
    local expected_status="$2"
    poll_until "Cart $expected_status" "$POLL_TIMEOUT_SECONDS" cart_is_status "$cart_id" "$expected_status"
    pass "Cart $expected_status"
}

reservation_is_status() {
    local expected_status="$1"
    RESERVATION_STATUS="$(query_reservation_db "$ORDER_ID")"
    POLL_LAST_OBSERVED="${RESERVATION_STATUS:-NOT_FOUND}"
    [[ "$RESERVATION_STATUS" == "$expected_status" ]]
}

wait_for_reservation_status() {
    local expected_status="$1"
    poll_until "Reservation $expected_status" "$POLL_TIMEOUT_SECONDS" reservation_is_status "$expected_status"
    pass "Reservation $expected_status"
}

saga_is_state() {
    local expected_state="$1"
    local expected_reason="${2:-}"
    local saga_row
    saga_row="$(query_workflow_db "$ORDER_ID")"
    SAGA_STATE="${saga_row%%|*}"
    SAGA_FAILURE_REASON="${saga_row#*|}"
    POLL_LAST_OBSERVED="${SAGA_STATE:-NOT_FOUND}${SAGA_FAILURE_REASON:+/$SAGA_FAILURE_REASON}"
    [[ "$SAGA_STATE" == "$expected_state" ]] && { [[ -z "$expected_reason" || "$SAGA_FAILURE_REASON" == "$expected_reason" ]]; }
}

wait_for_saga_state() {
    local expected_state="$1"
    local expected_reason="${2:-}"
    poll_until "Saga $expected_state${expected_reason:+/$expected_reason}" "$POLL_TIMEOUT_SECONDS" saga_is_state "$expected_state" "$expected_reason"
    if [[ -n "$expected_reason" ]]; then
        pass "Saga $expected_state ($expected_reason)"
    else
        pass "Saga $expected_state"
    fi
}

stock_is() {
    local expected="$1"
    STOCK_ROW="$(query_stock_db "$PRODUCT_A_ID")"
    POLL_LAST_OBSERVED="${STOCK_ROW:-NOT_FOUND}"
    [[ "$STOCK_ROW" == "$expected" ]]
}

wait_for_stock() {
    local expected="$1"
    poll_until "Stock Product A $expected" "$POLL_TIMEOUT_SECONDS" stock_is "$expected"
    case "$expected" in
        98\|0\|true) pass "Stock onHand=98 reserved=0" ;;
        100\|2\|true) pass "Stock during flow: onHand=100 reserved=2" ;;
        100\|0\|true) pass "Stock onHand=100 reserved=0" ;;
        *) pass "Stock Product A = $expected" ;;
    esac
}

get_provider_payment_id() {
    local payment_row
    payment_row="$(query_payment_db "$ORDER_ID")"
    PAYMENT_STATUS="${payment_row%%|*}"
    PROVIDER_PAYMENT_ID="${payment_row#*|}"
    POLL_LAST_OBSERVED="${PAYMENT_STATUS:-NOT_FOUND}"
    [[ "$PAYMENT_STATUS" == "AWAITING_CUSTOMER_ACTION" && "$PROVIDER_PAYMENT_ID" =~ ^cs_test_[A-Za-z0-9_]+$ ]]
}

wait_for_provider_payment_id() {
    poll_until "real Stripe Checkout Session id" "$POLL_TIMEOUT_SECONDS" get_provider_payment_id
}

expire_stripe_session() {
    local session_id="$1"
    local response_file status curl_status session_status
    response_file="$(mktemp "${TMPDIR:-/tmp}/e2e-stripe.XXXXXX")"

    if ! curl_status="$(curl --silent --show-error --connect-timeout 5 --max-time 30 \
        -u "${STRIPE_SECRET_KEY}:" -X POST \
        "https://api.stripe.com/v1/checkout/sessions/$session_id/expire" \
        -o "$response_file" -w '%{http_code}')"; then
        rm -f "$response_file"
        fail "Stripe Checkout Session expire request failed"
    fi

    status="$curl_status"
    if [[ "$status" != "200" ]]; then
        rm -f "$response_file"
        fail "Stripe Checkout Session expire request returned HTTP $status"
    fi

    session_status="$(jq -r '.status // empty' "$response_file")"
    rm -f "$response_file"
    [[ "$session_status" == "expired" ]] || fail "Stripe Checkout Session did not become expired"
    pass "Real Stripe Checkout Session expired"
}
