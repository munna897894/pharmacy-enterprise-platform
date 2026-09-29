#!/usr/bin/env sh
set -eu
# --delay-request 700: several scenarios use pm.execution.setNextRequest bounded polling loops
# (waiting for the async choreography saga to move an order/payment through states). Without a
# delay, Newman fires requests faster than Kafka consumers can react, causing flaky failures.
if command -v newman >/dev/null 2>&1; then
  newman run postman/pharmacy.postman_collection.json -e postman/local.postman_environment.json --delay-request 700 "$@"
else
  npx --no-install newman run postman/pharmacy.postman_collection.json -e postman/local.postman_environment.json --delay-request 700 "$@"
fi
