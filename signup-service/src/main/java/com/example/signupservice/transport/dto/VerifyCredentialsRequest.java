package com.example.signupservice.transport.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotBlank;

@JsonIgnoreProperties(ignoreUnknown = false)
public record VerifyCredentialsRequest(
        @NotBlank String email,
        @NotBlank String password
) {
}
