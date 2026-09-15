package com.example.apigateway.transport.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotBlank;

@JsonIgnoreProperties(ignoreUnknown = false)
public record LoginRequest(
        @NotBlank(message = "EMAIL_REQUIRED") String email,
        @NotBlank(message = "PASSWORD_REQUIRED") String password
) {
}
