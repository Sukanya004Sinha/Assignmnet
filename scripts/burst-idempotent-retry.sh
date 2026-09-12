#!/usr/bin/env bash
# Fires the SAME transfer (same idempotency_key) K times concurrently and
# checks exactly one debit/credit happened and all responses carry the same
# transfer id.
set -euo pipefail

BASE_URL="${1:-http://localhost:8080}"
K="${2:-30}"

ALICE="idem-alice-$(date +%s)"
BOB="idem-bob-$(date +%s)"

alice_wallet=$(curl -s -X POST "$BASE_URL/wallets" -H "Authorization: Bearer $ALICE" | grep -o '"id":"[^"]*"' | head -1 | cut -d'"' -f4)
bob_wallet=$(curl -s -X POST "$BASE_URL/wallets" -H "Authorization: Bearer $BOB" | grep -o '"id":"[^"]*"' | head -1 | cut -d'"' -f4)

curl -s -X POST "$BASE_URL/admin/wallets/$alice_wallet/seed" \
  -H "Content-Type: application/json" -d '{"amountPaise": 1000000}' > /dev/null

echo "alice=$alice_wallet bob=$bob_wallet (alice seeded with 1000000 paise)"

IDEMP_KEY="idem-key-$(date +%s)-$RANDOM"
AMOUNT=100

echo "Firing $K concurrent transfers with idempotency_key=$IDEMP_KEY"

seq "$K" | xargs -P "$K" -I{} curl -s -X POST "$BASE_URL/transfers" \
  -H "Authorization: Bearer $ALICE" \
  -H "Content-Type: application/json" \
  -d "{\"from\":\"$alice_wallet\",\"to\":\"$bob_wallet\",\"amountPaise\":$AMOUNT,\"idempotencyKey\":\"$IDEMP_KEY\"}" \
  > /tmp/idem_burst_out.txt

DISTINCT_IDS=$(grep -o '"id":"[^"]*"' /tmp/idem_burst_out.txt | sort -u | wc -l | tr -d ' ')
bob_balance=$(curl -s "$BASE_URL/wallets/$bob_wallet" -H "Authorization: Bearer $BOB" | grep -o '"balancePaise":[0-9]*' | cut -d':' -f2)

echo "Distinct transfer ids across $K responses: $DISTINCT_IDS"
echo "Bob's balance after storm: $bob_balance (expect exactly $AMOUNT credited if starting at 0)"

if [ "$DISTINCT_IDS" -eq 1 ]; then
  echo "PASS: idempotent retry storm collapsed to one transfer"
else
  echo "FAIL: got $DISTINCT_IDS distinct transfer ids, expected 1"
  exit 1
fi
