# Wallet & P2P Transfer Service

A small wallet service where users can hold a balance and send money to each other (peer-to-peer transfers). Built with Spring Boot 3, Java 21, and Postgres. All money is handled as whole paise (integers) never floats, never decimals  so there's no rounding weirdness.

## Running it locally

Just one command, no manual setup needed:

```bash
docker compose up --build
```

This spins up the app on `http://localhost:8080` and a Postgres database on port `5432`. Database tables are created automatically on startup (via Flyway migrations) — nothing to run by hand.

## How to call the API

Every request needs an `Authorization: Bearer <userId>` header. There's no real authentication here — the token is literally just the user's id — that's intentional since auth wasn't the focus of this exercise.

Here's what you can do:

- **`POST /wallets`** — creates a wallet for the caller if they don't have one yet, or just returns their existing one.
- **`GET /wallets/{id}`** — check a wallet's current balance.
- **`POST /transfers`** — send money from one wallet to another. Body looks like `{from, to, amountPaise, idempotencyKey}`.
- **`GET /transfers/{id}`** — look up the status of a transfer.
- **`POST /transfers/{id}/reverse`** — undo a completed transfer (send the money back). Body: `{idempotencyKey}`.
- **`POST /admin/wallets/{id}/seed`** — this one's just for testing. It credits a wallet directly with `{amountPaise}` so you have funds to play with when running the test scripts below. It's not part of the "real" API and isn't meant to be graded.

## Testing it under load

There are three scripts that hammer the API with concurrent requests to prove the important guarantees hold up (no double-spending, no lost money, etc.):

```bash
scripts/burst-get-or-create.sh    http://localhost:8080 50
scripts/burst-idempotent-retry.sh http://localhost:8080 30
scripts/burst-conservation.sh     http://localhost:8080 50
```

Each one prints a clear PASS or FAIL at the end.

## Keeping an eye on it

- **Logs** come out as structured JSON on stdout, and every request gets a correlation id (`X-Correlation-Id`) so you can trace a single request through all its log lines.
- **Metrics** are available at `GET /actuator/prometheus` — the usual request rate/latency stuff, plus a few custom counters that track what's actually happening with money: `wallet.transfers.created`, `wallet.transfers.declined_insufficient_funds`, and `wallet.transfers.idempotent_replay`.
- **Health check** lives at `GET /actuator/health`.

For the reasoning behind the design decisions (why this locking approach, where idempotency lives, etc.), check out `WRITEUP.md`.
