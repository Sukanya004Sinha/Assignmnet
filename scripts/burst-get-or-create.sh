#!/usr/bin/env bash
# Fires N concurrent POST /wallets for a brand-new user and checks that
# exactly one distinct wallet id comes back.
set -euo pipefail

BASE_URL="${1:-http://localhost:8080}"
N="${2:-50}"
USER_ID="burst-user-$(date +%s)-$RANDOM"
TOKEN="$USER_ID"

echo "Firing $N concurrent POST /wallets for user=$USER_ID against $BASE_URL"

seq "$N" | xargs -P "$N" -I{} curl -s -X POST "$BASE_URL/wallets" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  > /tmp/wallet_burst_out.txt

DISTINCT=$(grep -o '"id":"[^"]*"' /tmp/wallet_burst_out.txt | sort -u | wc -l | tr -d ' ')

echo "Distinct wallet ids returned: $DISTINCT"
if [ "$DISTINCT" -eq 1 ]; then
  echo "PASS: race-free get-or-create"
else
  echo "FAIL: expected 1 distinct wallet id, got $DISTINCT"
  exit 1
fi
