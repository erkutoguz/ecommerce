#!/usr/bin/env bash

set -Eeuo pipefail

BOOTSTRAP_SERVER="${KAFKA_BOOTSTRAP_SERVERS:-kafka:29092}"
KAFKA_TOPICS_BIN="/opt/kafka/bin/kafka-topics.sh"
EXPECTED_REPLICATION_FACTOR=1
MAX_ATTEMPTS=60

topics=(
  "customer.commands:3"
  "customer.commands.DLT:3"
  "product.events:3"
  "product.events.DLT:3"
  "stock.commands:3"
  "stock.commands.DLT:3"
  "stock.events:3"
  "stock.events.DLT:3"
  "payment.commands:3"
  "payment.commands.DLT:3"
  "payment.events:3"
  "payment.events.DLT:3"
  "order.commands:3"
  "order.commands.DLT:3"
  "order.events:3"
  "order.events.DLT:3"
)

describe_topic() {
  "${KAFKA_TOPICS_BIN}" \
    --bootstrap-server "${BOOTSTRAP_SERVER}" \
    --describe \
    --topic "$1" 2>/dev/null | head -n 1 || true
}

partition_count() {
  describe_topic "$1" \
    | awk -F 'PartitionCount:' 'NF > 1 { print $2 }' \
    | awk '{ print $1 }'
}

replication_factor() {
  describe_topic "$1" \
    | awk -F 'ReplicationFactor:' 'NF > 1 { print $2 }' \
    | awk '{ print $1 }'
}

wait_for_kafka() {
  local attempt

  for attempt in $(seq 1 "${MAX_ATTEMPTS}"); do
    if "${KAFKA_TOPICS_BIN}" \
      --bootstrap-server "${BOOTSTRAP_SERVER}" \
      --list >/dev/null 2>&1; then
      echo "Kafka is ready at ${BOOTSTRAP_SERVER}."
      return
    fi

    echo "Waiting for Kafka (${attempt}/${MAX_ATTEMPTS})..."
    sleep 2
  done

  echo "Kafka did not become ready at ${BOOTSTRAP_SERVER}." >&2
  exit 1
}

ensure_topic() {
  local topic="$1"
  local expected_partitions="$2"
  local actual_partitions
  local actual_replication_factor

  actual_partitions="$(partition_count "${topic}")"

  if [[ -z "${actual_partitions}" ]]; then
    echo "Creating topic ${topic} with ${expected_partitions} partitions."
    "${KAFKA_TOPICS_BIN}" \
      --bootstrap-server "${BOOTSTRAP_SERVER}" \
      --create \
      --if-not-exists \
      --topic "${topic}" \
      --partitions "${expected_partitions}" \
      --replication-factor "${EXPECTED_REPLICATION_FACTOR}"

    actual_partitions="$(partition_count "${topic}")"
  fi

  if [[ -z "${actual_partitions}" ]]; then
    echo "Could not inspect topic ${topic} after creation." >&2
    exit 1
  fi

  if (( actual_partitions < expected_partitions )); then
    echo "Expanding topic ${topic} from ${actual_partitions} to ${expected_partitions} partitions."
    "${KAFKA_TOPICS_BIN}" \
      --bootstrap-server "${BOOTSTRAP_SERVER}" \
      --alter \
      --topic "${topic}" \
      --partitions "${expected_partitions}"
    actual_partitions="$(partition_count "${topic}")"
  fi

  if (( actual_partitions > expected_partitions )); then
    echo "Topic ${topic} has ${actual_partitions} partitions; expected ${expected_partitions}." >&2
    exit 1
  fi

  actual_replication_factor="$(replication_factor "${topic}")"
  if [[ "${actual_replication_factor}" != "${EXPECTED_REPLICATION_FACTOR}" ]]; then
    echo "Topic ${topic} has replication factor ${actual_replication_factor}; expected ${EXPECTED_REPLICATION_FACTOR}." >&2
    exit 1
  fi

  echo "Verified ${topic}: partitions=${actual_partitions}, replication-factor=${actual_replication_factor}."
}

wait_for_kafka

for definition in "${topics[@]}"; do
  IFS=':' read -r topic expected_partitions <<< "${definition}"
  ensure_topic "${topic}" "${expected_partitions}"
done

echo "Kafka application topic initialization completed successfully."
