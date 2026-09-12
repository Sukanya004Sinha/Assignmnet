package com.paytm.wallet.repo;

import com.paytm.wallet.domain.Transfer;
import com.paytm.wallet.domain.TransferStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public class TransferRepository {

    private final JdbcTemplate jdbc;

    public TransferRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private static final RowMapper<Transfer> MAPPER = (rs, i) -> new Transfer(
            UUID.fromString(rs.getString("id")),
            UUID.fromString(rs.getString("from_wallet")),
            UUID.fromString(rs.getString("to_wallet")),
            rs.getLong("amount_paise"),
            TransferStatus.valueOf(rs.getString("status")),
            rs.getString("idempotency_key"),
            rs.getString("request_hash"),
            rs.getString("reversal_of") == null ? null : UUID.fromString(rs.getString("reversal_of")),
            rs.getTimestamp("created_at").toInstant(),
            rs.getTimestamp("updated_at").toInstant()
    );

    /**
     * Claims the idempotency key inside the same transaction as the caller's
     * ledger movement, via ON CONFLICT DO NOTHING rather than catching a
     * duplicate-key exception — on Postgres a caught constraint violation
     * still leaves the enclosing transaction aborted, so the insert must
     * complete normally either way. Returns the inserted PENDING row if this
     * call won the race, or empty if the key already exists (caller must then
     * compare request hashes and either replay the result or 409).
     */
    public Optional<Transfer> tryInsertPending(UUID id, UUID from, UUID to, long amount,
                                                String idempotencyKey, String requestHash, UUID reversalOf) {
        int inserted = jdbc.update(
                "INSERT INTO transfers (id, from_wallet, to_wallet, amount_paise, status, idempotency_key, request_hash, reversal_of) " +
                        "VALUES (?, ?, ?, ?, 'PENDING', ?, ?, ?) ON CONFLICT (idempotency_key) DO NOTHING",
                id, from, to, amount, idempotencyKey, requestHash, reversalOf);
        if (inserted == 1) {
            return findById(id);
        }
        return Optional.empty();
    }

    public Optional<Transfer> findByIdempotencyKey(String idempotencyKey) {
        return jdbc.query("SELECT * FROM transfers WHERE idempotency_key = ?", MAPPER, idempotencyKey)
                .stream().findFirst();
    }

    public Optional<Transfer> findById(UUID id) {
        return jdbc.query("SELECT * FROM transfers WHERE id = ?", MAPPER, id)
                .stream().findFirst();
    }

    public Optional<Transfer> findCompletedReversalOf(UUID transferId) {
        return jdbc.query(
                "SELECT * FROM transfers WHERE reversal_of = ? AND status = 'COMPLETED'", MAPPER, transferId)
                .stream().findFirst();
    }

    public void markCompleted(UUID id) {
        jdbc.update("UPDATE transfers SET status = 'COMPLETED', updated_at = now() WHERE id = ?", id);
    }

    public void markDeclined(UUID id) {
        jdbc.update("UPDATE transfers SET status = 'DECLINED', updated_at = now() WHERE id = ?", id);
    }
}
