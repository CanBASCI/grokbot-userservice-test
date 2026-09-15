package com.example.signupservice.transport.dto;

import java.util.UUID;

public record SignupResponse(UUID userId, String email) {
}
