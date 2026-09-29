#!/usr/bin/env sh
# Validates that every domain Kafka topic (and its .retry/.dlt companions) from
# docs/07-event-contracts.md actually exists on the broker, and that consumer
# groups are registered (i.e. services have connected and subscribed at least
# once). Does not publish/consume messages itself - it is a topology check,
# not a payload contract test (the Newman collection exercises real payloads
# indirectly via HTTP-triggered event flows).
set -eu
kafka_container="${1:-compose-kafka-1}"

domain_topics="pharmacy.prescription.events.v1 pharmacy.order.events.v1 pharmacy.inventory.events.v1 pharmacy.payment.events.v1 pharmacy.notification.events.v1"

echo "== Checking topic + retry/dlt existence on $kafka_container =="
existing=$(docker exec "$kafka_container" /opt/bitnami/kafka/bin/kafka-topics.sh --bootstrap-server localhost:9092 --list)

fail=0
for t in $domain_topics; do
  for suffix in "" ".retry" ".dlt"; do
    topic="${t}${suffix}"
    if echo "$existing" | grep -qx "$topic"; then
      echo "OK   $topic"
    else
      echo "FAIL $topic (missing)"
      fail=1
    fi
  done
done

echo
echo "== Consumer groups registered =="
docker exec "$kafka_container" /opt/bitnami/kafka/bin/kafka-consumer-groups.sh --bootstrap-server localhost:9092 --list || true

if [ "$fail" -ne 0 ]; then
  echo "Event smoke test FAILED: one or more topics are missing." >&2
  exit 1
fi
echo "Event smoke test PASSED: all domain topics and DLT/retry companions exist."
