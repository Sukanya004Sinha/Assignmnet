#!/usr/bin/env bash
# Seeds a small set of wallets, fires many concurrent transfers among them
# (including A->B and B->A at once, and some that would overdraw), then
# checks total balance is unchanged and no balance went negative.
set -euo pipefail

BASE_URL="${1:-http://localhost:8080}"
ROUNDS="${2:-200}"

STAMP=$(date +%s)
declare -a USERS=("cons-a-$STAMP" "cons-b-$STAMP" "cons-c-$STAMP")
declare -a WALLETS=()

for u in "${USERS[@]}"; do
  wid=$(curl -s -X POST "$BASE_URL/wallets" -H "Authorization: Bearer $u" | grep -o '"id":"[^"]*"' | head -1 | cut -d'"' -f4)
  curl -s -X POST "$BASE_URL/admin/wallets/$wid/seed" -H "Content-Type: application/json" -d '{"amountPaise": 10000}' > /dev/null
  WALLETS+=("$wid")
done

echo "Wallets: ${WALLETS[*]} (each seeded with 10000 paise; total=30000)"

fire_transfer() {
  local from="$1" to="$2" user="$3" amount="$4"
  curl -s -X POST "$BASE_URL/transfers" \
    -H "Authorization: Bearer $user" \
    -H "Content-Type: application/json" \
    -d "{\"from\":\"$from\",\"to\":\"$to\",\"amountPaise\":$amount,\"idempotencyKey\":\"cons-$RANDOM-$RANDOM-$$-$SECONDS\"}" \
    > /dev/null
}

echo "Firing $ROUNDS rounds of concurrent transfers among the 3 wallets (mix of directions and sizes, some overdrawing)..."

# Deterministic mixed pattern each round: fire A->B, B->A, B->C, C->B concurrently.
for i in $(seq 1 "$ROUNDS"); do
  amt=$(( (RANDOM % 3000) + 1 ))
  fire_transfer "${WALLETS[0]}" "${WALLETS[1]}" "${USERS[0]}" "$amt" &
  fire_transfer "${WALLETS[1]}" "${WALLETS[0]}" "${USERS[1]}" "$amt" &
  fire_transfer "${WALLETS[1]}" "${WALLETS[2]}" "${USERS[1]}" "$amt" &
  fire_transfer "${WALLETS[2]}" "${WALLETS[1]}" "${USERS[2]}" "$amt" &
  if (( i % 20 == 0 )); then wait; fi
done
wait

total=0
for wid in "${WALLETS[@]}"; do
  bal=$(curl -s "$BASE_URL/wallets/$wid" -H "Authorization: Bearer ${USERS[0]}" | grep -o '"balancePaise":[0-9-]*' | cut -d':' -f2)
  echo "wallet $wid balance = $bal"
  if [ "$bal" -lt 0 ]; then
    echo "FAIL: negative balance detected in wallet $wid"
    exit 1
  fi
  total=$(( total + bal ))
done

echo "Total balance after contention: $total (expect 30000)"
if [ "$total" -eq 30000 ]; then
  echo "PASS: conservation held under contention, no negative balances"
else
  echo "FAIL: total balance drifted from 30000 to $total"
  exit 1
fi
