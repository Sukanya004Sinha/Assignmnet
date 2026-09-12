package com.paytm.wallet.api.dto;

import jakarta.validation.constraints.NotBlank;

public record ReverseRequest(@NotBlank String idempotencyKey) {
}
