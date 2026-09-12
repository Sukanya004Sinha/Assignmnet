package com.paytm.wallet.api.dto;

import com.paytm.wallet.domain.Transfer;
import com.paytm.wallet.domain.TransferStatus;

import java.time.Instant;
import java.util.UUID;

public record TransferResponse(
        UUID id,
        UUID from,
        UUID to,
        long amountPaise,
        TransferStatus status,
        String idempotencyKey,
        UUID reversalOf,
        Instant createdAt,
        Instant updatedAt
) {
    public static TransferResponse from(Transfer t) {
        return new TransferResponse(t.id(), t.fromWallet(), t.toWallet(), t.amountPaise(),
                t.status(), t.idempotencyKey(), t.reversalOf(), t.createdAt(), t.updatedAt());
    }
}
