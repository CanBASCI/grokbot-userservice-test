package com.example.signupservice.transport.grpc;

import com.example.auth.signup.v1.RegisterRequest;
import com.example.auth.signup.v1.RegisterResponse;
import com.example.auth.signup.v1.VerifyCredentialsRequest;
import com.example.auth.signup.v1.VerifyCredentialsResponse;
import com.example.signupservice.domain.error.DomainException;
import com.example.signupservice.domain.error.ErrorCode;
import com.example.signupservice.domain.model.User;
import com.example.signupservice.domain.port.SignupIdempotencyStore;
import com.example.signupservice.domain.usecase.SignupService;
import com.example.signupservice.domain.usecase.VerifyCredentialsService;
import com.example.signupservice.infrastructure.RequestFingerprint;
import com.google.protobuf.Any;
import com.google.rpc.ErrorInfo;
import io.grpc.Context;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.protobuf.StatusProto;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SignupGrpcServiceTest {

    @Mock
    private SignupService signupService;
    @Mock
    private VerifyCredentialsService verifyCredentialsService;
    @Mock
    private SignupIdempotencyStore idempotencyStore;
    @Mock
    private StreamObserver<RegisterResponse> registerObserver;
    @Mock
    private StreamObserver<VerifyCredentialsResponse> verifyObserver;

    private SignupGrpcService grpcService;
    private Context previous;

    @BeforeEach
    void setUp() {
        grpcService = new SignupGrpcService(signupService, verifyCredentialsService, idempotencyStore);
        previous = Context.current().attach();
    }

    @AfterEach
    void tearDown() {
        Context.current().detach(previous);
    }

    @Test
    void registerSuccess() {
        UUID id = UUID.randomUUID();
        when(idempotencyStore.find("key-1")).thenReturn(Optional.empty());
        when(signupService.signup("user@example.com", "password123"))
                .thenReturn(new User(id, "user@example.com", "hash", Instant.now()));

        runWithKey("key-1", () -> grpcService.register(
                RegisterRequest.newBuilder().setEmail("user@example.com").setPassword("password123").build(),
                registerObserver
        ));

        ArgumentCaptor<RegisterResponse> captor = ArgumentCaptor.forClass(RegisterResponse.class);
        verify(registerObserver).onNext(captor.capture());
        verify(registerObserver).onCompleted();
        assertEquals(id.toString(), captor.getValue().getUserId());
        assertEquals("user@example.com", captor.getValue().getEmail());
        verify(idempotencyStore).save(
                eq("key-1"),
                eq(RequestFingerprint.ofSignup("user@example.com", "password123")),
                eq(id),
                eq("user@example.com")
        );
    }

    @Test
    void registerReplaySameFingerprint() {
        UUID id = UUID.randomUUID();
        String hash = RequestFingerprint.ofSignup("user@example.com", "password123");
        when(idempotencyStore.find("key-1")).thenReturn(Optional.of(
                new SignupIdempotencyStore.Record("key-1", hash, id, "user@example.com")
        ));

        runWithKey("key-1", () -> grpcService.register(
                RegisterRequest.newBuilder().setEmail("user@example.com").setPassword("password123").build(),
                registerObserver
        ));

        ArgumentCaptor<RegisterResponse> captor = ArgumentCaptor.forClass(RegisterResponse.class);
        verify(registerObserver).onNext(captor.capture());
        verify(signupService, never()).signup(anyString(), anyString());
        assertEquals(id.toString(), captor.getValue().getUserId());
    }

    @Test
    void registerMismatchBody() {
        UUID id = UUID.randomUUID();
        when(idempotencyStore.find("key-1")).thenReturn(Optional.of(
                new SignupIdempotencyStore.Record("key-1", "other-hash", id, "user@example.com")
        ));

        runWithKey("key-1", () -> grpcService.register(
                RegisterRequest.newBuilder().setEmail("user@example.com").setPassword("password123").build(),
                registerObserver
        ));

        ArgumentCaptor<Throwable> captor = ArgumentCaptor.forClass(Throwable.class);
        verify(registerObserver).onError(captor.capture());
        StatusRuntimeException sre = (StatusRuntimeException) captor.getValue();
        assertEquals(Status.Code.ALREADY_EXISTS, sre.getStatus().getCode());
        assertEquals("IDEMPOTENCY_KEY_BODY_MISMATCH", sre.getTrailers().get(GrpcStatusMapper.ERROR_CODE_KEY));
        assertErrorInfoReason(sre, "IDEMPOTENCY_KEY_BODY_MISMATCH");
    }

    @Test
    void registerMissingIdempotencyKey() {
        grpcService.register(
                RegisterRequest.newBuilder().setEmail("user@example.com").setPassword("password123").build(),
                registerObserver
        );

        ArgumentCaptor<Throwable> captor = ArgumentCaptor.forClass(Throwable.class);
        verify(registerObserver).onError(captor.capture());
        StatusRuntimeException sre = (StatusRuntimeException) captor.getValue();
        assertEquals(Status.Code.INVALID_ARGUMENT, sre.getStatus().getCode());
        assertEquals("IDEMPOTENCY_KEY_REQUIRED", sre.getTrailers().get(GrpcStatusMapper.ERROR_CODE_KEY));
    }

    @Test
    void registerEmailTakenMapsToAlreadyExists() {
        when(idempotencyStore.find("key-1")).thenReturn(Optional.empty());
        when(signupService.signup(anyString(), anyString()))
                .thenThrow(new DomainException(ErrorCode.EMAIL_TAKEN, "Email is already registered"));

        runWithKey("key-1", () -> grpcService.register(
                RegisterRequest.newBuilder().setEmail("user@example.com").setPassword("password123").build(),
                registerObserver
        ));

        ArgumentCaptor<Throwable> captor = ArgumentCaptor.forClass(Throwable.class);
        verify(registerObserver).onError(captor.capture());
        StatusRuntimeException sre = (StatusRuntimeException) captor.getValue();
        assertEquals(Status.Code.ALREADY_EXISTS, sre.getStatus().getCode());
        assertEquals("EMAIL_TAKEN", sre.getTrailers().get(GrpcStatusMapper.ERROR_CODE_KEY));
        assertErrorInfoReason(sre, "EMAIL_TAKEN");
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
                .thenThrow(new DomainException(ErrorCode.INVALID_CREDENTIALS, "Invalid credentials"));

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
        assertErrorInfoReason(sre, "INVALID_CREDENTIALS");
    }

    private static void runWithKey(String key, Runnable action) {
        Context ctx = Context.current().withValue(IdempotencyKeyInterceptor.IDEMPOTENCY_KEY, key);
        Context previous = ctx.attach();
        try {
            action.run();
        } finally {
            ctx.detach(previous);
        }
    }

    private static void assertErrorInfoReason(StatusRuntimeException sre, String expected) {
        var status = StatusProto.fromThrowable(sre);
        assertNotNull(status);
        boolean found = false;
        for (Any detail : status.getDetailsList()) {
            if (detail.is(ErrorInfo.class)) {
                try {
                    assertEquals(expected, detail.unpack(ErrorInfo.class).getReason());
                    found = true;
                } catch (Exception e) {
                    throw new AssertionError(e);
                }
            }
        }
        assertTrue(found, "Expected ErrorInfo reason " + expected);
    }
}
