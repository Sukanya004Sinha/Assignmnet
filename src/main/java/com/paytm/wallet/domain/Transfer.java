package com.paytm.wallet.domain;

import java.time.Instant;
import java.util.UUID;

public record Transfer(
        UUID id,
        UUID fromWallet,
        UUID toWallet,
        long amountPaise,
        TransferStatus status,
        String idempotencyKey,
        String requestHash,
        UUID reversalOf,
        Instant createdAt,
        Instant updatedAt
) {
}
