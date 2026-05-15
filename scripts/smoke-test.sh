#!/usr/bin/env bash
# smoke-test.sh — end-to-end test of the booking flow against a running stack
#
# Defaults assume `docker-compose up` is running on localhost.
# Override with TRAVEL_URL when targeting a cluster:
#   TRAVEL_URL=http://<ingress-ip>/api/v1/trips ./smoke-test.sh
set -euo pipefail

TRAVEL_URL="${TRAVEL_URL:-http://localhost:8080/api/v1/trips}"
START_DATE="${START_DATE:-$(date -d '+30 days' '+%Y-%m-%d' 2>/dev/null || date -v+30d '+%Y-%m-%d')}"
END_DATE="${END_DATE:-$(date -d '+35 days' '+%Y-%m-%d' 2>/dev/null || date -v+35d '+%Y-%m-%d')}"

echo "═══ SCENARIO 1: happy path (hotel + flight available) ═══"
RESP=$(curl -s -X POST "$TRAVEL_URL" \
    -H "Content-Type: application/json" \
    -d "{
        \"customerName\":\"Alice\",
        \"customerEmail\":\"alice@example.com\",
        \"originCity\":\"New York\",
        \"destinationCity\":\"Paris\",
        \"startDate\":\"$START_DATE\",
        \"endDate\":\"$END_DATE\",
        \"numberOfTravelers\":2,
        \"numberOfRooms\":1
    }")
echo "$RESP"
TRIP_REF=$(echo "$RESP" | grep -o '"tripReference":"[^"]*"' | cut -d'"' -f4)
echo "Trip reference: $TRIP_REF"

echo ""
echo "═══ Cancelling trip $TRIP_REF (triggers serverless cancellation function) ═══"
curl -s -X DELETE "$TRAVEL_URL/$TRIP_REF"
echo ""

echo ""
echo "═══ SCENARIO 2: hotel unavailable (impossible city) ═══"
curl -s -X POST "$TRAVEL_URL" \
    -H "Content-Type: application/json" \
    -d "{
        \"customerName\":\"Bob\",
        \"customerEmail\":\"bob@example.com\",
        \"originCity\":\"New York\",
        \"destinationCity\":\"Atlantis\",
        \"startDate\":\"$START_DATE\",
        \"endDate\":\"$END_DATE\",
        \"numberOfTravelers\":1,
        \"numberOfRooms\":1
    }"
echo ""
echo ""
echo "✅ Smoke tests complete."
