package com.paytm.wallet.domain;

import com.paytm.wallet.repo.TransferRepository;
import com.paytm.wallet.repo.WalletRepository;
import com.paytm.wallet.util.RequestHasher;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class TransferService {

    private static final Logger log = LoggerFactory.getLogger(TransferService.class);

    private final WalletRepository walletRepository;
    private final TransferRepository transferRepository;
    private final MeterRegistry meterRegistry;

    public TransferService(WalletRepository walletRepository, TransferRepository transferRepository,
                            MeterRegistry meterRegistry) {
        this.walletRepository = walletRepository;
        this.transferRepository = transferRepository;
        this.meterRegistry = meterRegistry;
    }

    /**
     * Exactly-once transfer. The idempotency key's uniqueness is committed in
     * the SAME transaction as the debit/credit (see tryInsertPending) — that
     * is what rules out the TOCTOU where two concurrent requests both pass a
     * "does it exist?" check before either has written anything.
     *
     * No-overdraft / conservation: the debit is a single atomic conditional
     * UPDATE (balance_paise >= amount) with no app-side read-modify-write, so
     * there is no lost-update window even when many transfers touch the same
     * wallets at once, including A->B and B->A concurrently — there is no
     * multi-row lock to order, so no deadlock potential either.
     */
    @Transactional
    public Transfer transfer(UUID from, UUID to, long amountPaise, String idempotencyKey) {
        if (from.equals(to)) {
            throw new IllegalArgumentException("from and to wallets must differ");
        }
        if (amountPaise <= 0) {
            throw new IllegalArgumentException("amount_paise must be positive");
        }
        String requestHash = RequestHasher.hash(from, to, amountPaise);
        return submit(UUID.randomUUID(), from, to, amountPaise, idempotencyKey, requestHash, null);
    }

    /**
     * Reversal reuses the exact same primitive with roles swapped: recipient
     * debits, original sender credits. Its own idempotency key means
     * reversing twice with the SAME key replays the one result rather than
     * erroring — the "already reversed" guard below only fires for a
     * genuinely new key, so a retry of the same reversal request is
     * indistinguishable from a normal idempotent replay. If the recipient no
     * longer has the funds, the conditional debit declines cleanly rather
     * than letting the balance go negative.
     */
    @Transactional
    public Transfer reverse(UUID originalTransferId, String idempotencyKey) {
        Transfer original = transferRepository.findById(originalTransferId)
                .orElseThrow(() -> new NotFoundException("transfer not found: " + originalTransferId));
        if (original.status() != TransferStatus.COMPLETED) {
            throw new IllegalStateException("only a completed transfer can be reversed: " + originalTransferId);
        }
        String requestHash = RequestHasher.hash(original.toWallet(), original.fromWallet(), original.amountPaise());
        UUID candidateId = UUID.randomUUID();

        var inserted = transferRepository.tryInsertPending(candidateId, original.toWallet(), original.fromWallet(),
                original.amountPaise(), idempotencyKey, requestHash, originalTransferId);
        if (inserted.isEmpty()) {
            return replayOrConflict(idempotencyKey, requestHash);
        }

        boolean alreadyReversed = transferRepository.findCompletedReversalOf(originalTransferId)
                .filter(existing -> !existing.id().equals(candidateId))
                .isPresent();
        if (alreadyReversed) {
            transferRepository.markDeclined(candidateId);
            log.info("transfer.reversal_declined_already_reversed transferId={} originalTransferId={}",
                    candidateId, originalTransferId);
            return transferRepository.findById(candidateId).orElseThrow();
        }

        return applyMovement(inserted.get());
    }

    private Transfer submit(UUID candidateId, UUID from, UUID to, long amountPaise,
                             String idempotencyKey, String requestHash, UUID reversalOf) {
        var inserted = transferRepository.tryInsertPending(candidateId, from, to, amountPaise,
                idempotencyKey, requestHash, reversalOf);

        if (inserted.isEmpty()) {
            return replayOrConflict(idempotencyKey, requestHash);
        }

        return applyMovement(inserted.get());
    }

    private Transfer replayOrConflict(String idempotencyKey, String requestHash) {
        Transfer existing = transferRepository.findByIdempotencyKey(idempotencyKey)
                .orElseThrow(() -> new IllegalStateException("idempotency key vanished: " + idempotencyKey));
        if (!existing.requestHash().equals(requestHash)) {
            log.warn("transfer.idempotency_conflict idempotencyKey={}", idempotencyKey);
            throw new IdempotencyConflictException(idempotencyKey);
        }
        log.info("transfer.idempotent_replay transferId={} idempotencyKey={}", existing.id(), idempotencyKey);
        meterRegistry.counter("wallet.transfers.idempotent_replay").increment();
        return existing;
    }

    private Transfer applyMovement(Transfer transfer) {
        UUID from = transfer.fromWallet();
        UUID to = transfer.toWallet();
        long amountPaise = transfer.amountPaise();

        log.info("transfer.created transferId={} from={} to={} amountPaise={}",
                transfer.id(), from, to, amountPaise);

        boolean debited = walletRepository.tryDebit(from, amountPaise);
        if (!debited) {
            transferRepository.markDeclined(transfer.id());
            log.info("transfer.declined_insufficient_funds transferId={} from={} amountPaise={}",
                    transfer.id(), from, amountPaise);
            meterRegistry.counter("wallet.transfers.declined_insufficient_funds").increment();
            return transferRepository.findById(transfer.id()).orElseThrow();
        }
        log.info("transfer.debited transferId={} from={} amountPaise={}", transfer.id(), from, amountPaise);

        walletRepository.credit(to, amountPaise);
        log.info("transfer.credited transferId={} to={} amountPaise={}", transfer.id(), to, amountPaise);

        transferRepository.markCompleted(transfer.id());
        meterRegistry.counter("wallet.transfers.created").increment();
        return transferRepository.findById(transfer.id()).orElseThrow();
    }

    public Transfer getById(UUID id) {
        return transferRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("transfer not found: " + id));
    }
}
