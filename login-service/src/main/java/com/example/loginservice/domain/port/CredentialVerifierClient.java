package com.example.loginservice.domain.port;

import com.example.loginservice.domain.model.AuthenticatedUser;

import java.util.Optional;

public interface CredentialVerifierClient {

    Optional<AuthenticatedUser> verify(String email, String password);
}
