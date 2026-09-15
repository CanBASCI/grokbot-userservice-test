package com.example.apigateway.transport.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotBlank;

@JsonIgnoreProperties(ignoreUnknown = false)
public record RefreshRequest(
        @NotBlank(message = "REFRESH_TOKEN_REQUIRED") String refreshToken
) {
}
