package com.paytm.wallet.api.dto;

import com.paytm.wallet.domain.Wallet;

import java.util.UUID;

public record WalletResponse(UUID id, String userId, long balancePaise) {
    public static WalletResponse from(Wallet wallet) {
        return new WalletResponse(wallet.id(), wallet.userId(), wallet.balancePaise());
    }
}
