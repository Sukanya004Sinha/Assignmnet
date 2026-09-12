package com.paytm.wallet.domain;

import com.paytm.wallet.repo.WalletRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class WalletService {

    private static final Logger log = LoggerFactory.getLogger(WalletService.class);

    private final WalletRepository walletRepository;

    public WalletService(WalletRepository walletRepository) {
        this.walletRepository = walletRepository;
    }

    @Transactional
    public Wallet getOrCreate(String userId) {
        Wallet wallet = walletRepository.getOrCreate(userId);
        log.info("wallet.get_or_create userId={} walletId={} balancePaise={}",
                userId, wallet.id(), wallet.balancePaise());
        return wallet;
    }

    public Wallet getById(UUID walletId) {
        return walletRepository.findById(walletId)
                .orElseThrow(() -> new NotFoundException("wallet not found: " + walletId));
    }
}
