package com.example.loginservice.infrastructure;

import com.example.auth.signup.v1.SignupServiceGrpc;
import com.example.auth.signup.v1.VerifyCredentialsRequest;
import com.example.auth.signup.v1.VerifyCredentialsResponse;
import com.example.loginservice.domain.model.AuthenticatedUser;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GrpcCredentialVerifierClientTest {

    @Mock
    private SignupServiceGrpc.SignupServiceBlockingStub signupStub;

    @Test
    void verifySuccess() {
        UUID id = UUID.randomUUID();
        when(signupStub.verifyCredentials(any(VerifyCredentialsRequest.class)))
                .thenReturn(VerifyCredentialsResponse.newBuilder()
                        .setUserId(id.toString())
                        .setEmail("user@example.com")
                        .build());

        GrpcCredentialVerifierClient client = new GrpcCredentialVerifierClient(signupStub);
        Optional<AuthenticatedUser> result = client.verify("user@example.com", "password123");
        assertTrue(result.isPresent());
        assertEquals(id, result.get().getUserId());
        assertEquals("user@example.com", result.get().getEmail());
    }

    @Test
    void verifyUnauthenticatedReturnsEmpty() {
        when(signupStub.verifyCredentials(any(VerifyCredentialsRequest.class)))
                .thenThrow(Status.UNAUTHENTICATED.asRuntimeException());

        GrpcCredentialVerifierClient client = new GrpcCredentialVerifierClient(signupStub);
        assertTrue(client.verify("user@example.com", "wrong").isEmpty());
    }

    @Test
    void verifyOtherStatusWrapsAsIllegalState() {
        when(signupStub.verifyCredentials(any(VerifyCredentialsRequest.class)))
                .thenThrow(Status.UNAVAILABLE.asRuntimeException());

        GrpcCredentialVerifierClient client = new GrpcCredentialVerifierClient(signupStub);
        IllegalStateException ex = assertThrows(
                IllegalStateException.class,
                () -> client.verify("user@example.com", "password123")
        );
        assertEquals("credential verify failed", ex.getMessage());
        assertInstanceOf(StatusRuntimeException.class, ex.getCause());
    }
}
