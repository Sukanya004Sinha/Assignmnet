# Wallet & P2P Transfer Service

Spring Boot 3 / Java 21 / Postgres wallet service. Money is always integer paise.

## Run locally

```bash
docker compose up --build
```

App comes up on `http://localhost:8080`, Postgres on `5432`. Flyway runs migrations on startup.

## API

Auth: `Authorization: Bearer <userId>` (the token is the user id — no real auth, intentionally out of scope).

- `POST /wallets` — get-or-create the caller's wallet.
- `GET /wallets/{id}` — balance.
- `POST /transfers` — body: `{from, to, amountPaise, idempotencyKey}`.
- `GET /transfers/{id}` — status.
- `POST /transfers/{id}/reverse` — body: `{idempotencyKey}`. Reverses a completed transfer.
- `POST /admin/wallets/{id}/seed` — body: `{amountPaise}`. **Test-setup only**, not part of the graded surface — credits a wallet directly so burst scripts have funds to move.

## Burst scripts

```bash
scripts/burst-get-or-create.sh   http://localhost:8080 50
scripts/burst-idempotent-retry.sh http://localhost:8080 30
scripts/burst-conservation.sh    http://localhost:8080 50
```

## Observability

- Structured JSON logs (Logstash encoder) to stdout, correlation id (`X-Correlation-Id`) via MDC on every line.
- Metrics: `GET /actuator/prometheus` — request rate/latency (Micrometer default HTTP metrics) plus domain counters `wallet.transfers.created`, `wallet.transfers.declined_insufficient_funds`, `wallet.transfers.idempotent_replay`.
- Health: `GET /actuator/health`.

See `WRITEUP.md` for the design reasoning.
