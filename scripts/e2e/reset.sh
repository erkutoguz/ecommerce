#!/usr/bin/env bash

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=scripts/e2e/common.sh
source "$SCRIPT_DIR/common.sh"

require_command docker
require_command curl
require_command jq
require_payment_env_file

info "Resetting local development environment"
compose down -v --remove-orphans
compose up -d --build

wait_for_gateway
verify_seed_data

pass "E2E environment reset and ready"
