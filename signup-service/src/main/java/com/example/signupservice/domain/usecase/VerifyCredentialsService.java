package com.example.signupservice.domain.usecase;

import com.example.signupservice.domain.error.DomainException;
import com.example.signupservice.domain.error.ErrorCode;
import com.example.signupservice.domain.model.User;
import com.example.signupservice.domain.port.PasswordHasher;
import com.example.signupservice.domain.port.UserRepository;

import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

public final class VerifyCredentialsService {

    /**
     * Precomputed BCrypt hash used only so missing-user paths still pay a matches() cost.
     * Not a real account password.
     */
    static final String DUMMY_BCRYPT_HASH =
            "$2a$10$dXJ3SW6G7P50lGmMkkmwe.20cQQubK3.HZWzG3YB1tlRy.fqvM/BG";

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
        Optional<User> found = userRepository.findByEmail(normalized);
        String hashToCheck = found.map(User::getPasswordHash).orElse(DUMMY_BCRYPT_HASH);
        boolean matches = passwordHasher.matches(password, hashToCheck);
        if (found.isEmpty() || !matches) {
            throw invalidCredentials();
        }
        return found.get();
    }

    private static DomainException invalidCredentials() {
        return new DomainException(ErrorCode.INVALID_CREDENTIALS, "Invalid credentials");
    }
}
