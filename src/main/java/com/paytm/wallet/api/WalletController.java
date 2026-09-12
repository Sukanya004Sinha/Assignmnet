package com.paytm.wallet.api;

import com.paytm.wallet.api.dto.WalletResponse;
import com.paytm.wallet.domain.WalletService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

import static com.paytm.wallet.web.AuthFilter.USER_ATTR;

@RestController
public class WalletController {

    private final WalletService walletService;

    public WalletController(WalletService walletService) {
        this.walletService = walletService;
    }

    @PostMapping("/wallets")
    public ResponseEntity<WalletResponse> getOrCreate(HttpServletRequest request) {
        String userId = (String) request.getAttribute(USER_ATTR);
        var wallet = walletService.getOrCreate(userId);
        return ResponseEntity.status(HttpStatus.OK).body(WalletResponse.from(wallet));
    }

    @GetMapping("/wallets/{id}")
    public WalletResponse getById(@PathVariable UUID id) {
        return WalletResponse.from(walletService.getById(id));
    }
}
