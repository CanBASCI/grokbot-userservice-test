package com.example.signupservice.domain.usecase;

import com.example.signupservice.domain.error.DomainException;
import com.example.signupservice.domain.error.ErrorCode;
import com.example.signupservice.domain.model.User;
import com.example.signupservice.domain.port.PasswordHasher;
import com.example.signupservice.domain.port.UserRepository;

import java.util.Locale;
import java.util.Objects;

public final class VerifyCredentialsService {

    private final UserRepository userRepository;
    private final PasswordHasher passwordHasher;

    public VerifyCredentialsService(UserRepository userRepository, PasswordHasher passwordHasher) {
        this.userRepository = Objects.requireNonNull(userRepository);
        this.passwordHasher = Objects.requireNonNull(passwordHasher);
    }

    public User verify(String email, String password) {
        if (email == null || email.isBlank() || password == null || password.isBlank()) {
            throw invalidCredentials();
        }
        String normalized = email.trim().toLowerCase(Locale.ROOT);
        User user = userRepository.findByEmail(normalized)
                .orElseThrow(VerifyCredentialsService::invalidCredentials);
        if (!passwordHasher.matches(password, user.getPasswordHash())) {
            throw invalidCredentials();
        }
        return user;
    }

    private static DomainException invalidCredentials() {
        return new DomainException(
                ErrorCode.INVALID_CREDENTIALS,
                401,
                "Unauthorized",
                "Invalid credentials"
        );
    }
}
