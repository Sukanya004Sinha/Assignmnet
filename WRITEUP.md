# Design write-up

## Data model

```
wallets(id uuid pk, user_id text unique, balance_paise bigint check(>=0))
transfers(id uuid pk, from_wallet uuid, to_wallet uuid, amount_paise bigint,
          status text, idempotency_key text unique, request_hash text,
          reversal_of uuid nullable fk->transfers, created_at, updated_at)
```

Money is `bigint` paise throughout — no floats, no decimal rupees, anywhere from the wire format to the DB column.

## Simplest-correct mechanism for conservation + no-overdraft

Debit is a single atomic conditional `UPDATE wallets SET balance_paise = balance_paise - ? WHERE id = ? AND balance_paise >= ?`. Zero rows affected means insufficient funds — decline cleanly, no partial apply. Credit is an unconditional `+= amount` update on the recipient.

This was chosen over:
- **`SELECT ... FOR UPDATE` with sorted lock order** — correct, but needs an explicit `LEAST(from,to)`-first locking discipline to avoid deadlock when A→B and B→A run concurrently, and holds row locks for the length of the transaction. The conditional `UPDATE` never needs to hold two locks at once — it touches one row per statement — so there is no deadlock to avoid by construction.
- **Full `SERIALIZABLE` isolation** — correct but pessimistic: it makes the DB detect conflicts after the fact and forces the app to retry aborted transactions, adding retry-loop complexity for a guarantee the conditional `UPDATE` already gives at READ COMMITTED.

Two conditional `UPDATE`s (debit then credit) run inside one `@Transactional` method, so either both apply or the whole transfer rolls back — that's what keeps the sum invariant.

## Where idempotency lives

The `idempotency_key` has a `UNIQUE` constraint on the `transfers` table, and the insert of the `PENDING` transfer row happens in the **same transaction** as the debit/credit — not a pre-check in a separate transaction. Concretely: `INSERT ... ON CONFLICT (idempotency_key) DO NOTHING`. If this call wins the insert, it proceeds to debit/credit in the same transaction. If it loses (row already exists), Postgres blocks on the unique index until the other transaction commits, then this call sees the final committed status and either replays the identical result (same request hash) or returns `409` (different hash) — no TOCTOU window.

`ON CONFLICT DO NOTHING` is used instead of catching a duplicate-key exception because Postgres aborts the entire enclosing transaction on any statement error, including a caught constraint violation — an UPSERT that completes without erroring is required to keep this composable inside the surrounding `@Transactional` call.

## Consistency vs availability

Chose strict consistency over availability: every write goes through one Postgres primary inside a transaction: no eventual-consistency window, no "optimistic" client-side retries that could double-apply. For a money workload this is the correct trade — a wallet balance that's briefly unavailable during a DB failover is fine; a wallet balance that's briefly *wrong* is not. What's given up: horizontal write scaling and multi-region availability, which this exercise's scale doesn't need.

## Reversal

Reuses the same `submit()` primitive with `from`/`to` swapped and its own `idempotency_key`, tagged with `reversal_of`. Reversing twice concurrently collapses to one refund via the same idempotency mechanism. If the recipient already spent the funds, the conditional debit declines cleanly instead of allowing a negative balance.

## AI directed vs decided

Directed: overall architecture (Spring Boot + JDBC over JPA to keep the SQL explicit, conditional-UPDATE approach, idempotency-in-same-transaction design, Docker/compose layout).
Let AI decide: exact class/package layout, DTO field naming, log message wording, Dockerfile flag choices (`MaxRAMPercentage`).

## Free-tier cost

₹0 — Render/Railway free web service + free Postgres tier; no paid add-ons used.
