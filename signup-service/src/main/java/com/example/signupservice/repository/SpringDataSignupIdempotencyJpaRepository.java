package com.example.signupservice.repository;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SpringDataSignupIdempotencyJpaRepository extends JpaRepository<SignupIdempotencyEntity, String> {
}
