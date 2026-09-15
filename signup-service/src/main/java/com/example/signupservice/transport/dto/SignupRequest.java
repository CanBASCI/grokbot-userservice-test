package com.example.signupservice.transport.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@JsonIgnoreProperties(ignoreUnknown = false)
public record SignupRequest(
        @NotBlank(message = "EMAIL_REQUIRED")
        @Email(message = "EMAIL_INVALID")
        String email,
        @NotBlank(message = "PASSWORD_REQUIRED")
        @Size.List({
                @Size(min = 8, message = "PASSWORD_TOO_SHORT"),
                @Size(max = 128, message = "PASSWORD_TOO_LONG")
        })
        String password
) {
}
