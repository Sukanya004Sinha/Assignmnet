package com.paytm.wallet.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;

public final class RequestHasher {

    private RequestHasher() {
    }

    public static String hash(UUID from, UUID to, long amountPaise) {
        return hash(from.toString() + ":" + to.toString() + ":" + amountPaise);
    }

    public static String hash(String canonical) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(canonical.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(bytes);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
