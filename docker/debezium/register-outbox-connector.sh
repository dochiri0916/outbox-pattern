#!/bin/sh
set -eu

connector_url="http://connect:8083/connectors/outbox-mysql-connector/config"

curl --fail --silent --show-error \
  --retry 30 \
  --retry-delay 2 \
  --retry-connrefused \
  -X PUT \
  -H 'Content-Type: application/json' \
  --data-binary @/outbox-connector.json \
  "$connector_url"
