package com.example.signupservice.domain;

import com.example.signupservice.domain.error.DomainException;
import com.example.signupservice.domain.error.ErrorCode;
import com.example.signupservice.domain.model.User;
import com.example.signupservice.domain.port.PasswordHasher;
import com.example.signupservice.domain.port.UserRepository;
import com.example.signupservice.domain.usecase.VerifyCredentialsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VerifyCredentialsServiceTest {

    private FakeUserRepository userRepository;
    private FakePasswordHasher passwordHasher;
    private VerifyCredentialsService service;

    @BeforeEach
    void setUp() {
        userRepository = new FakeUserRepository();
        passwordHasher = new FakePasswordHasher();
        service = new VerifyCredentialsService(userRepository, passwordHasher);
        userRepository.save(new User(UUID.randomUUID(), "user@example.com", "hashed:secret", Instant.now()));
    }

    @Test
    void verifySuccess() {
        User user = service.verify("User@Example.com", "secret");
        assertEquals("user@example.com", user.getEmail());
    }

    @Test
    void verifyWrongPassword() {
        DomainException ex = assertThrows(DomainException.class, () -> service.verify("user@example.com", "wrong"));
        assertEquals(ErrorCode.INVALID_CREDENTIALS, ex.getCode());
        assertEquals(401, ex.getStatus());
    }

    @Test
    void verifyUnknownUserStillRunsMatches() {
        int before = passwordHasher.matchesCalls.get();
        DomainException ex = assertThrows(DomainException.class, () -> service.verify("other@example.com", "secret"));
        assertEquals(ErrorCode.INVALID_CREDENTIALS, ex.getCode());
        assertTrue(passwordHasher.matchesCalls.get() > before);
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
            return Optional.ofNullable(byEmail.get(email));
        }

        @Override
        public Optional<User> findById(UUID id) {
            return byEmail.values().stream().filter(u -> u.getId().equals(id)).findFirst();
        }
    }

    private static final class FakePasswordHasher implements PasswordHasher {
        final AtomicInteger matchesCalls = new AtomicInteger();

        @Override
        public String hash(String rawPassword) {
            return "hashed:" + rawPassword;
        }

        @Override
        public boolean matches(String rawPassword, String passwordHash) {
            matchesCalls.incrementAndGet();
            return passwordHash.equals(hash(rawPassword));
        }
    }
}
