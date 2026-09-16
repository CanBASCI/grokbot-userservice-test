package com.example.apigateway.transport.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@JsonIgnoreProperties(ignoreUnknown = false)
public record LoginRequest(
        @NotBlank(message = "EMAIL_REQUIRED") String email,
        @NotBlank(message = "PASSWORD_REQUIRED")
        @Size(max = 128, message = "PASSWORD_TOO_LONG")
        String password
) {
}
