package com.example.apigateway.transport.rest;

import com.example.apigateway.transport.advice.GatewayException;

final class IdempotencyKeys {

    private IdempotencyKeys() {
    }

    static void requireValid(String key) {
        if (key == null || key.isBlank()) {
            throw new GatewayException("IDEMPOTENCY_KEY_REQUIRED", 400, "Bad Request", "Idempotency-Key is required");
        }
        if (key.length() > 128) {
            throw new GatewayException("IDEMPOTENCY_KEY_INVALID", 400, "Bad Request", "Idempotency-Key is too long");
        }
        for (int i = 0; i < key.length(); i++) {
            char c = key.charAt(i);
            if (c < 0x20 || c > 0x7E) {
                throw new GatewayException(
                        "IDEMPOTENCY_KEY_INVALID",
                        400,
                        "Bad Request",
                        "Idempotency-Key must be printable ASCII"
                );
            }
        }
    }
}
