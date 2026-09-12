package com.paytm.wallet.api;

import com.paytm.wallet.api.dto.ReverseRequest;
import com.paytm.wallet.api.dto.TransferRequest;
import com.paytm.wallet.api.dto.TransferResponse;
import com.paytm.wallet.domain.TransferService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
public class TransferController {

    private final TransferService transferService;

    public TransferController(TransferService transferService) {
        this.transferService = transferService;
    }

    @PostMapping("/transfers")
    public TransferResponse create(@Valid @RequestBody TransferRequest req) {
        var transfer = transferService.transfer(req.from(), req.to(), req.amountPaise(), req.idempotencyKey());
        return TransferResponse.from(transfer);
    }

    @GetMapping("/transfers/{id}")
    public TransferResponse getById(@PathVariable UUID id) {
        return TransferResponse.from(transferService.getById(id));
    }

    @PostMapping("/transfers/{id}/reverse")
    public TransferResponse reverse(@PathVariable UUID id, @Valid @RequestBody ReverseRequest req) {
        var reversal = transferService.reverse(id, req.idempotencyKey());
        return TransferResponse.from(reversal);
    }
}
