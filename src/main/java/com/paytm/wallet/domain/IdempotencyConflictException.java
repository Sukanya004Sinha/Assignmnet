package com.paytm.wallet.domain;

/** Same idempotency key reused with a different request body. Maps to HTTP 409. */
public class IdempotencyConflictException extends RuntimeException {
    public IdempotencyConflictException(String idempotencyKey) {
        super("idempotency key already used with a different request body: " + idempotencyKey);
    }
}
