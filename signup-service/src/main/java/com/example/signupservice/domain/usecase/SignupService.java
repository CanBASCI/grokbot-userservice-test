package com.example.signupservice.domain.usecase;

import com.example.signupservice.domain.error.DomainException;
import com.example.signupservice.domain.error.ErrorCode;
import com.example.signupservice.domain.model.User;
import com.example.signupservice.domain.port.PasswordHasher;
import com.example.signupservice.domain.port.UserRepository;

import java.time.Clock;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

public final class SignupService {

    public static final int PASSWORD_MIN_LENGTH = 8;
    public static final int PASSWORD_MAX_LENGTH = 128;

    private static final Pattern EMAIL_PATTERN = Pattern.compile(
            "^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$"
    );

    private final UserRepository userRepository;
    private final PasswordHasher passwordHasher;
    private final Clock clock;

    public SignupService(UserRepository userRepository, PasswordHasher passwordHasher, Clock clock) {
        this.userRepository = Objects.requireNonNull(userRepository);
        this.passwordHasher = Objects.requireNonNull(passwordHasher);
        this.clock = Objects.requireNonNull(clock);
    }

    public User signup(String email, String password) {
        String normalizedEmail = validateAndNormalizeEmail(email);
        validatePassword(password);

        if (userRepository.existsByEmail(normalizedEmail)) {
            throw new DomainException(ErrorCode.EMAIL_TAKEN, "Email is already registered");
        }

        User user = new User(
                UUID.randomUUID(),
                normalizedEmail,
                passwordHasher.hash(password),
                clock.instant()
        );
        return userRepository.save(user);
    }

    static String validateAndNormalizeEmail(String email) {
        if (email == null || email.isBlank()) {
            throw new DomainException(ErrorCode.EMAIL_REQUIRED, "Email is required");
        }
        String normalized = email.trim().toLowerCase(Locale.ROOT);
        if (!EMAIL_PATTERN.matcher(normalized).matches()) {
            throw new DomainException(ErrorCode.EMAIL_INVALID, "Email format is invalid");
        }
        return normalized;
    }

    static void validatePassword(String password) {
        if (password == null || password.isBlank()) {
            throw new DomainException(ErrorCode.PASSWORD_REQUIRED, "Password is required");
        }
        if (password.length() < PASSWORD_MIN_LENGTH) {
            throw new DomainException(
                    ErrorCode.PASSWORD_TOO_SHORT,
                    "Password must be at least " + PASSWORD_MIN_LENGTH + " characters"
            );
        }
        if (password.length() > PASSWORD_MAX_LENGTH) {
            throw new DomainException(
                    ErrorCode.PASSWORD_TOO_LONG,
                    "Password must be at most " + PASSWORD_MAX_LENGTH + " characters"
            );
        }
    }
}
