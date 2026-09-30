#!/usr/bin/env sh
set -eu

bootstrap_server="${KAFKA_LOCAL_BOOTSTRAP_SERVERS:-localhost:19092}"
topics="pharmacy.prescription.events.v1 pharmacy.order.events.v1 pharmacy.inventory.events.v1 pharmacy.payment.events.v1 pharmacy.notification.events.v1"

kafka-topics --bootstrap-server "$bootstrap_server" --list >/dev/null
for topic in $topics; do
  kafka-topics --bootstrap-server "$bootstrap_server" --create --if-not-exists \
    --topic "$topic" --partitions 3 --replication-factor 1
  for suffix in retry dlt; do
    kafka-topics --bootstrap-server "$bootstrap_server" --create --if-not-exists \
      --topic "$topic.$suffix" --partitions 1 --replication-factor 1
  done
done

existing=$(kafka-topics --bootstrap-server "$bootstrap_server" --list)
for topic in $topics; do
  for suffix in "" ".retry" ".dlt"; do
    if ! printf '%s\n' "$existing" | grep -qx "$topic$suffix"; then
      echo "Missing Kafka topic $topic$suffix." >&2
      exit 1
    fi
  done
done
echo "All local domain, retry and DLT topics are ready."
