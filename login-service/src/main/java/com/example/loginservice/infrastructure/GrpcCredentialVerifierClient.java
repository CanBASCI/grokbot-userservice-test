package com.example.loginservice.infrastructure;

import com.example.auth.signup.v1.SignupServiceGrpc;
import com.example.auth.signup.v1.VerifyCredentialsRequest;
import com.example.auth.signup.v1.VerifyCredentialsResponse;
import com.example.loginservice.domain.model.AuthenticatedUser;
import com.example.loginservice.domain.port.CredentialVerifierClient;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class GrpcCredentialVerifierClient implements CredentialVerifierClient {

    private final SignupServiceGrpc.SignupServiceBlockingStub signupStub;

    public GrpcCredentialVerifierClient(SignupServiceGrpc.SignupServiceBlockingStub signupStub) {
        this.signupStub = Objects.requireNonNull(signupStub);
    }

    @Override
    public Optional<AuthenticatedUser> verify(String email, String password) {
        try {
            VerifyCredentialsResponse response = signupStub.verifyCredentials(
                    VerifyCredentialsRequest.newBuilder()
                            .setEmail(email)
                            .setPassword(password)
                            .build()
            );
            if (response.getUserId().isBlank() || response.getEmail().isBlank()) {
                return Optional.empty();
            }
            return Optional.of(new AuthenticatedUser(UUID.fromString(response.getUserId()), response.getEmail()));
        } catch (StatusRuntimeException ex) {
            if (ex.getStatus().getCode() == Status.Code.UNAUTHENTICATED) {
                return Optional.empty();
            }
            throw new IllegalStateException("credential verify failed", ex);
        }
    }
}
