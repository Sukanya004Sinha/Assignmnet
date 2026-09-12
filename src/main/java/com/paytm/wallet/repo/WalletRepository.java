package com.paytm.wallet.repo;

import com.paytm.wallet.domain.Wallet;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public class WalletRepository {

    private final JdbcTemplate jdbc;

    public WalletRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private static final RowMapper<Wallet> MAPPER = (rs, i) -> new Wallet(
            UUID.fromString(rs.getString("id")),
            rs.getString("user_id"),
            rs.getLong("balance_paise")
    );

    /**
     * Race-free get-or-create: relies on the UNIQUE constraint on user_id, not
     * an app-side check-then-insert. ON CONFLICT DO NOTHING (rather than
     * catching a duplicate-key exception) matters on Postgres: a caught
     * constraint violation still aborts the enclosing transaction, so a
     * normal-completing UPSERT is the only way to keep this composable inside
     * a larger @Transactional call. Either this call wins the insert or loses
     * it to a concurrent one and re-selects — exactly one wallet ever exists.
     */
    public Wallet getOrCreate(String userId) {
        UUID candidateId = UUID.randomUUID();
        int inserted = jdbc.update(
                "INSERT INTO wallets (id, user_id, balance_paise) VALUES (?, ?, 0) ON CONFLICT (user_id) DO NOTHING",
                candidateId, userId);
        if (inserted == 1) {
            return new Wallet(candidateId, userId, 0);
        }
        return findByUserId(userId)
                .orElseThrow(() -> new IllegalStateException("wallet insert lost race but no row found for " + userId));
    }

    public Optional<Wallet> findByUserId(String userId) {
        return jdbc.query("SELECT * FROM wallets WHERE user_id = ?", MAPPER, userId)
                .stream().findFirst();
    }

    public Optional<Wallet> findById(UUID id) {
        return jdbc.query("SELECT * FROM wallets WHERE id = ?", MAPPER, id)
                .stream().findFirst();
    }

    /**
     * Atomic conditional debit. Returns true iff the balance had enough funds
     * and was decremented — no app-side read-modify-write, so there is no lost
     * update window under concurrent debits against the same wallet.
     */
    public boolean tryDebit(UUID walletId, long amountPaise) {
        int rows = jdbc.update(
                "UPDATE wallets SET balance_paise = balance_paise - ? WHERE id = ? AND balance_paise >= ?",
                amountPaise, walletId, amountPaise);
        return rows == 1;
    }

    public void credit(UUID walletId, long amountPaise) {
        int rows = jdbc.update(
                "UPDATE wallets SET balance_paise = balance_paise + ? WHERE id = ?",
                amountPaise, walletId);
        if (rows != 1) {
            throw new IllegalStateException("credit target wallet not found: " + walletId);
        }
    }
}
