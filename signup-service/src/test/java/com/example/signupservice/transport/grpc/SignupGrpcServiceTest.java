package com.example.signupservice.transport.grpc;

import com.example.auth.signup.v1.RegisterRequest;
import com.example.auth.signup.v1.RegisterResponse;
import com.example.auth.signup.v1.VerifyCredentialsRequest;
import com.example.auth.signup.v1.VerifyCredentialsResponse;
import com.example.signupservice.domain.error.DomainException;
import com.example.signupservice.domain.error.ErrorCode;
import com.example.signupservice.domain.model.User;
import com.example.signupservice.domain.usecase.SignupService;
import com.example.signupservice.domain.usecase.VerifyCredentialsService;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SignupGrpcServiceTest {

    @Mock
    private SignupService signupService;
    @Mock
    private VerifyCredentialsService verifyCredentialsService;
    @Mock
    private StreamObserver<RegisterResponse> registerObserver;
    @Mock
    private StreamObserver<VerifyCredentialsResponse> verifyObserver;

    private SignupGrpcService grpcService;

    @BeforeEach
    void setUp() {
        grpcService = new SignupGrpcService(signupService, verifyCredentialsService);
    }

    @Test
    void registerSuccess() {
        UUID id = UUID.randomUUID();
        when(signupService.signup("user@example.com", "password123"))
                .thenReturn(new User(id, "user@example.com", "hash", Instant.now()));

        grpcService.register(
                RegisterRequest.newBuilder().setEmail("user@example.com").setPassword("password123").build(),
                registerObserver
        );

        ArgumentCaptor<RegisterResponse> captor = ArgumentCaptor.forClass(RegisterResponse.class);
        verify(registerObserver).onNext(captor.capture());
        verify(registerObserver).onCompleted();
        assertEquals(id.toString(), captor.getValue().getUserId());
        assertEquals("user@example.com", captor.getValue().getEmail());
    }

    @Test
    void registerEmailTakenMapsToAlreadyExists() {
        when(signupService.signup(anyString(), anyString()))
                .thenThrow(new DomainException(ErrorCode.EMAIL_TAKEN, 409, "Conflict", "Email is already registered"));

        grpcService.register(
                RegisterRequest.newBuilder().setEmail("user@example.com").setPassword("password123").build(),
                registerObserver
        );

        ArgumentCaptor<Throwable> captor = ArgumentCaptor.forClass(Throwable.class);
        verify(registerObserver).onError(captor.capture());
        StatusRuntimeException sre = (StatusRuntimeException) captor.getValue();
        assertEquals(Status.Code.ALREADY_EXISTS, sre.getStatus().getCode());
        assertEquals("EMAIL_TAKEN", sre.getTrailers().get(GrpcStatusMapper.ERROR_CODE_KEY));
    }

    @Test
    void verifyCredentialsSuccess() {
        UUID id = UUID.randomUUID();
        when(verifyCredentialsService.verify("user@example.com", "password123"))
                .thenReturn(new User(id, "user@example.com", "hash", Instant.now()));

        grpcService.verifyCredentials(
                VerifyCredentialsRequest.newBuilder().setEmail("user@example.com").setPassword("password123").build(),
                verifyObserver
        );

        ArgumentCaptor<VerifyCredentialsResponse> captor = ArgumentCaptor.forClass(VerifyCredentialsResponse.class);
        verify(verifyObserver).onNext(captor.capture());
        verify(verifyObserver).onCompleted();
        assertEquals(id.toString(), captor.getValue().getUserId());
    }

    @Test
    void verifyInvalidCredentialsMapsToUnauthenticated() {
        when(verifyCredentialsService.verify(anyString(), anyString()))
                .thenThrow(new DomainException(ErrorCode.INVALID_CREDENTIALS, 401, "Unauthorized", "Invalid credentials"));

        grpcService.verifyCredentials(
                VerifyCredentialsRequest.newBuilder().setEmail("user@example.com").setPassword("wrong").build(),
                verifyObserver
        );

        ArgumentCaptor<Throwable> captor = ArgumentCaptor.forClass(Throwable.class);
        verify(verifyObserver).onError(captor.capture());
        StatusRuntimeException sre = (StatusRuntimeException) captor.getValue();
        assertEquals(Status.Code.UNAUTHENTICATED, sre.getStatus().getCode());
        assertNotNull(sre.getTrailers());
        assertEquals("INVALID_CREDENTIALS", sre.getTrailers().get(GrpcStatusMapper.ERROR_CODE_KEY));
    }
}
