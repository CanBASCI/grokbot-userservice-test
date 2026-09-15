package com.example.signupservice.domain;

import com.example.signupservice.domain.error.DomainException;
import com.example.signupservice.domain.error.ErrorCode;
import com.example.signupservice.domain.model.User;
import com.example.signupservice.domain.port.PasswordHasher;
import com.example.signupservice.domain.port.UserRepository;
import com.example.signupservice.domain.usecase.SignupService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SignupServiceTest {

    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

    private FakeUserRepository userRepository;
    private FakePasswordHasher passwordHasher;
    private SignupService signupService;

    @BeforeEach
    void setUp() {
        userRepository = new FakeUserRepository();
        passwordHasher = new FakePasswordHasher();
        signupService = new SignupService(
                userRepository,
                passwordHasher,
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    @Test
    void signupSuccessNormalizesEmail() {
        User user = signupService.signup("User@Example.COM", "password123");
        assertNotNull(user.getId());
        assertEquals("user@example.com", user.getEmail());
        assertEquals("hashed:password123", user.getPasswordHash());
        assertEquals(NOW, user.getCreatedAt());
        assertTrue(userRepository.existsByEmail("user@example.com"));
    }

    @Test
    void signupEmailTaken() {
        userRepository.save(new User(UUID.randomUUID(), "taken@example.com", "h", NOW));
        DomainException ex = assertThrows(
                DomainException.class,
                () -> signupService.signup("taken@example.com", "password123")
        );
        assertEquals(ErrorCode.EMAIL_TAKEN, ex.getCode());
        assertEquals(409, ex.getStatus());
    }

    @Test
    void emailRequired() {
        DomainException ex = assertThrows(DomainException.class, () -> signupService.signup("  ", "password123"));
        assertEquals(ErrorCode.EMAIL_REQUIRED, ex.getCode());
    }

    @Test
    void emailInvalid() {
        DomainException ex = assertThrows(DomainException.class, () -> signupService.signup("not-an-email", "password123"));
        assertEquals(ErrorCode.EMAIL_INVALID, ex.getCode());
    }

    @Test
    void passwordRequired() {
        DomainException ex = assertThrows(DomainException.class, () -> signupService.signup("a@b.co", " "));
        assertEquals(ErrorCode.PASSWORD_REQUIRED, ex.getCode());
    }

    @Test
    void passwordTooShort() {
        DomainException ex = assertThrows(DomainException.class, () -> signupService.signup("a@b.co", "short"));
        assertEquals(ErrorCode.PASSWORD_TOO_SHORT, ex.getCode());
    }

    @Test
    void passwordTooLong() {
        String longPassword = "x".repeat(129);
        DomainException ex = assertThrows(DomainException.class, () -> signupService.signup("a@b.co", longPassword));
        assertEquals(ErrorCode.PASSWORD_TOO_LONG, ex.getCode());
    }

    private static final class FakeUserRepository implements UserRepository {
        private final Map<String, User> byEmail = new HashMap<>();

        @Override
        public boolean existsByEmail(String email) {
            return byEmail.containsKey(email);
        }

        @Override
        public User save(User user) {
            byEmail.put(user.getEmail(), user);
            return user;
        }

        @Override
        public Optional<User> findByEmail(String email) {
            return Optional.ofNullable(byEmail.get(email.toLowerCase(Locale.ROOT)));
        }

        @Override
        public Optional<User> findById(UUID id) {
            return byEmail.values().stream().filter(u -> u.getId().equals(id)).findFirst();
        }
    }

    private static final class FakePasswordHasher implements PasswordHasher {
        @Override
        public String hash(String rawPassword) {
            return "hashed:" + rawPassword;
        }

        @Override
        public boolean matches(String rawPassword, String passwordHash) {
            return passwordHash.equals(hash(rawPassword));
        }
    }
}
