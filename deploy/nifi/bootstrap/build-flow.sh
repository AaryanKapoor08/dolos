#!/bin/sh
# ---------------------------------------------------------------------------
# Dolos — NiFi flow bootstrap (Phase 1G)
#
# Builds, via NiFi's REST API, a small dataflow that turns a dropped CSV into a
# bulk NDJSON feed for ingestion-service:
#
#   GetFile (watch /opt/dolos/incoming/*.csv)
#        -> ConvertRecord (CSVReader -> JsonRecordSetWriter, one JSON per line)
#        -> InvokeHTTP (POST application/x-ndjson to /ingest/transactions)
#
# NiFi runs unsecured over HTTP here (NIFI_WEB_HTTP_PORT set, no TLS), so every
# REST call is anonymous — fine for local $0 dev infra, never for production.
#
# Re-runnable: an existing "Dolos CSV Feed" group is stopped and deleted first,
# so `docker compose up` (which starts NiFi with an empty flow) always lands a
# clean build. Needs: curl, jq.
# ---------------------------------------------------------------------------
set -eu

NIFI_API="${NIFI_API:-http://nifi:8080/nifi-api}"
INGEST_URL="${INGEST_URL:-http://ingestion-service:8082/ingest/transactions}"
WATCH_DIR="${WATCH_DIR:-/opt/dolos/incoming}"
PG_NAME="Dolos CSV Feed"
CID="dolos-bootstrap"

say() { echo "[nifi-bootstrap] $*"; }

# --- helpers ---------------------------------------------------------------
# POST/PUT/DELETE/GET wrappers that fail loudly on HTTP errors.
api() { # method path [json-body]
  _m="$1"; _p="$2"; _b="${3:-}"
  if [ -n "$_b" ]; then
    curl -sS -f -X "$_m" -H 'Content-Type: application/json' -d "$_b" "${NIFI_API}${_p}"
  else
    curl -sS -f -X "$_m" "${NIFI_API}${_p}"
  fi
}

# Wait until NiFi's REST API answers.
wait_for_nifi() {
  say "waiting for NiFi at ${NIFI_API} ..."
  i=0
  until curl -sS -f "${NIFI_API}/flow/about" >/dev/null 2>&1; do
    i=$((i + 1))
    if [ "$i" -gt 120 ]; then say "NiFi did not become ready in time"; exit 1; fi
    sleep 5
  done
  say "NiFi is up."
}

# --- reset -----------------------------------------------------------------
ROOT_ID=$(api GET /process-groups/root | jq -r '.id')

delete_existing_group() {
  existing=$(api GET "/process-groups/${ROOT_ID}/process-groups" \
    | jq -r --arg n "$PG_NAME" '.processGroups[]? | select(.component.name==$n) | .id')
  [ -z "$existing" ] && return 0
  say "removing existing group ${existing}"
  # A group can only be deleted once its processors are STOPPED, its controller services
  # DISABLED, and its queues empty — so unwind it in that order before deleting.
  api PUT "/flow/process-groups/${existing}" "{\"id\":\"${existing}\",\"state\":\"STOPPED\"}" >/dev/null 2>&1 || true
  for cs in $(api GET "/flow/process-groups/${existing}/controller-services" \
      | jq -r --arg g "$existing" '.controllerServices[]? | select(.component.parentGroupId==$g) | .id'); do
    csver=$(api GET "/controller-services/${cs}" | jq -r '.revision.version')
    api PUT "/controller-services/${cs}/run-status" \
      "{\"revision\":{\"version\":${csver},\"clientId\":\"${CID}\"},\"state\":\"DISABLED\"}" >/dev/null 2>&1 || true
  done
  api POST "/process-groups/${existing}/empty-all-connections-requests" '{}' >/dev/null 2>&1 || true
  sleep 3
  ver=$(api GET "/process-groups/${existing}" | jq -r '.revision.version')
  api DELETE "/process-groups/${existing}?version=${ver}&clientId=${CID}" >/dev/null
}

# --- build -----------------------------------------------------------------
create_group() {
  body="{\"revision\":{\"version\":0,\"clientId\":\"${CID}\"},\"component\":{\"name\":\"${PG_NAME}\",\"position\":{\"x\":0,\"y\":0}}}"
  api POST "/process-groups/${ROOT_ID}/process-groups" "$body" | jq -r '.id'
}

# Create a controller service, returning its id. $1=type $2=name
create_cs() {
  body="{\"revision\":{\"version\":0,\"clientId\":\"${CID}\"},\"component\":{\"type\":\"$1\",\"name\":\"$2\"}}"
  api POST "/process-groups/${PG}/controller-services" "$body" | jq -r '.id'
}

# Set properties on a controller service. $1=id $2=properties-json
configure_cs() {
  ver=$(api GET "/controller-services/$1" | jq -r '.revision.version')
  body="{\"revision\":{\"version\":${ver},\"clientId\":\"${CID}\"},\"component\":{\"id\":\"$1\",\"properties\":$2}}"
  api PUT "/controller-services/$1" "$body" >/dev/null
}

enable_cs() {
  ver=$(api GET "/controller-services/$1" | jq -r '.revision.version')
  api PUT "/controller-services/$1/run-status" "{\"revision\":{\"version\":${ver},\"clientId\":\"${CID}\"},\"state\":\"ENABLED\"}" >/dev/null
}

# Enabling is asynchronous; a processor that references a not-yet-ENABLED service won't start.
# Block until the service reports ENABLED before we start the flow.
wait_cs_enabled() {
  i=0
  until [ "$(api GET "/controller-services/$1" | jq -r '.component.state')" = "ENABLED" ]; do
    i=$((i + 1))
    if [ "$i" -gt 30 ]; then say "controller service $1 did not enable in time"; exit 1; fi
    sleep 1
  done
}

# Create a processor, returning its id. $1=type $2=name $3=x $4=y
create_proc() {
  body="{\"revision\":{\"version\":0,\"clientId\":\"${CID}\"},\"component\":{\"type\":\"$1\",\"name\":\"$2\",\"position\":{\"x\":$3,\"y\":$4}}}"
  api POST "/process-groups/${PG}/processors" "$body" | jq -r '.id'
}

# Configure a processor. $1=id $2=config-json (the .config object)
configure_proc() {
  ver=$(api GET "/processors/$1" | jq -r '.revision.version')
  body="{\"revision\":{\"version\":${ver},\"clientId\":\"${CID}\"},\"component\":{\"id\":\"$1\",\"config\":$2}}"
  api PUT "/processors/$1" "$body" >/dev/null
}

# Connect two processors on a relationship. $1=src $2=dst $3=relationship
connect() {
  body="{\"revision\":{\"version\":0,\"clientId\":\"${CID}\"},\"component\":{\"source\":{\"id\":\"$1\",\"groupId\":\"${PG}\",\"type\":\"PROCESSOR\"},\"destination\":{\"id\":\"$2\",\"groupId\":\"${PG}\",\"type\":\"PROCESSOR\"},\"selectedRelationships\":[\"$3\"]}}"
  api POST "/process-groups/${PG}/connections" "$body" >/dev/null
}

main() {
  wait_for_nifi
  delete_existing_group
  PG=$(create_group)
  say "created group ${PG}"

  reader=$(create_cs "org.apache.nifi.csv.CSVReader" "Dolos CSV Reader")
  writer=$(create_cs "org.apache.nifi.json.JsonRecordSetWriter" "Dolos NDJSON Writer")
  # CSVReader: derive string fields from the CSV header line (consumes it as the header).
  # JsonRecordSetWriter: one JSON object per line (NDJSON) — it already defaults to writing no
  # embedded schema and inheriting the reader's record schema.
  configure_cs "$reader" '{"schema-access-strategy":"csv-header-derived"}'
  configure_cs "$writer" '{"output-grouping":"output-oneline"}'
  enable_cs "$reader"
  enable_cs "$writer"
  wait_cs_enabled "$reader"
  wait_cs_enabled "$writer"
  say "controller services ready"

  getfile=$(create_proc "org.apache.nifi.processors.standard.GetFile" "Watch incoming CSV" 0 0)
  convert=$(create_proc "org.apache.nifi.processors.standard.ConvertRecord" "CSV to NDJSON" 0 200)
  invoke=$(create_proc "org.apache.nifi.processors.standard.InvokeHTTP" "POST to ingestion" 0 400)

  configure_proc "$getfile" "{\"properties\":{\"Input Directory\":\"${WATCH_DIR}\",\"File Filter\":\".*\\\\.csv\",\"Keep Source File\":\"false\"},\"schedulingPeriod\":\"5 sec\"}"
  configure_proc "$convert" "{\"properties\":{\"record-reader\":\"${reader}\",\"record-writer\":\"${writer}\"},\"autoTerminatedRelationships\":[\"failure\"]}"
  configure_proc "$invoke" "{\"properties\":{\"HTTP Method\":\"POST\",\"Remote URL\":\"${INGEST_URL}\",\"Content-Type\":\"application/x-ndjson\"},\"autoTerminatedRelationships\":[\"Original\",\"Response\",\"Retry\",\"No Retry\",\"Failure\"]}"
  say "processors configured"

  connect "$getfile" "$convert" "success"
  connect "$convert" "$invoke" "success"
  say "connections wired"

  api PUT "/flow/process-groups/${PG}" "{\"id\":\"${PG}\",\"state\":\"RUNNING\"}" >/dev/null
  say "flow started — drop a CSV into ${WATCH_DIR} on the host bind mount."
}

main "$@"
