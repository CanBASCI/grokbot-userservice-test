package com.example.signupservice.transport.dto;

import java.util.UUID;

public record VerifyCredentialsResponse(UUID userId, String email) {
}
