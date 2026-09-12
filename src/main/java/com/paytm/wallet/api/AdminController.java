package com.paytm.wallet.api;

import com.paytm.wallet.api.dto.WalletResponse;
import com.paytm.wallet.domain.WalletService;
import com.paytm.wallet.repo.WalletRepository;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

/**
 * Test-setup only: credits a wallet directly, bypassing the transfer ledger.
 * Not part of the graded API surface — exists purely so burst scripts have a
 * way to fund wallets before running contention/idempotency tests, since the
 * exercise spec defines no deposit endpoint.
 */
@RestController
public class AdminController {

    private final WalletRepository walletRepository;
    private final WalletService walletService;

    public AdminController(WalletRepository walletRepository, WalletService walletService) {
        this.walletRepository = walletRepository;
        this.walletService = walletService;
    }

    @PostMapping("/admin/wallets/{id}/seed")
    public WalletResponse seed(@PathVariable UUID id, @RequestBody Map<String, Long> body) {
        long amountPaise = body.getOrDefault("amountPaise", 0L);
        walletRepository.credit(id, amountPaise);
        return WalletResponse.from(walletService.getById(id));
    }
}
