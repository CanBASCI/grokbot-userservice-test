package com.example.loginservice.transport.grpc;

import com.example.auth.login.v1.LoginRequest;
import com.example.auth.login.v1.LoginResponse;
import com.example.auth.login.v1.RefreshRequest;
import com.example.auth.login.v1.RefreshResponse;
import com.example.loginservice.domain.error.DomainException;
import com.example.loginservice.domain.error.ErrorCode;
import com.example.loginservice.domain.model.TokenPair;
import com.example.loginservice.domain.usecase.LoginService;
import com.example.loginservice.domain.usecase.RefreshService;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LoginGrpcServiceTest {

    @Mock
    private LoginService loginService;
    @Mock
    private RefreshService refreshService;
    @Mock
    private StreamObserver<LoginResponse> loginObserver;
    @Mock
    private StreamObserver<RefreshResponse> refreshObserver;

    private LoginGrpcService grpcService;

    @BeforeEach
    void setUp() {
        grpcService = new LoginGrpcService(loginService, refreshService);
    }

    @Test
    void loginSuccess() {
        when(loginService.login("user@example.com", "password123"))
                .thenReturn(new TokenPair("access", "refresh", "Bearer", 900));

        grpcService.login(
                LoginRequest.newBuilder().setEmail("user@example.com").setPassword("password123").build(),
                loginObserver
        );

        ArgumentCaptor<LoginResponse> captor = ArgumentCaptor.forClass(LoginResponse.class);
        verify(loginObserver).onNext(captor.capture());
        verify(loginObserver).onCompleted();
        assertEquals("access", captor.getValue().getAccessToken());
        assertEquals("refresh", captor.getValue().getRefreshToken());
        assertEquals("Bearer", captor.getValue().getTokenType());
        assertEquals(900, captor.getValue().getExpiresIn());
    }

    @Test
    void loginInvalidCredentials() {
        when(loginService.login(anyString(), anyString()))
                .thenThrow(new DomainException(ErrorCode.INVALID_CREDENTIALS, "Invalid credentials"));

        grpcService.login(
                LoginRequest.newBuilder().setEmail("user@example.com").setPassword("wrong").build(),
                loginObserver
        );

        ArgumentCaptor<Throwable> captor = ArgumentCaptor.forClass(Throwable.class);
        verify(loginObserver).onError(captor.capture());
        StatusRuntimeException sre = (StatusRuntimeException) captor.getValue();
        assertEquals(Status.Code.UNAUTHENTICATED, sre.getStatus().getCode());
        assertEquals("INVALID_CREDENTIALS", sre.getTrailers().get(GrpcStatusMapper.ERROR_CODE_KEY));
    }

    @Test
    void refreshSuccess() {
        when(refreshService.refresh("old-refresh"))
                .thenReturn(new TokenPair("access2", "refresh2", "Bearer", 900));

        grpcService.refresh(
                RefreshRequest.newBuilder().setRefreshToken("old-refresh").build(),
                refreshObserver
        );

        ArgumentCaptor<RefreshResponse> captor = ArgumentCaptor.forClass(RefreshResponse.class);
        verify(refreshObserver).onNext(captor.capture());
        verify(refreshObserver).onCompleted();
        assertEquals("access2", captor.getValue().getAccessToken());
    }

    @Test
    void refreshInvalid() {
        when(refreshService.refresh(anyString()))
                .thenThrow(new DomainException(ErrorCode.INVALID_REFRESH_TOKEN, "Invalid refresh token"));

        grpcService.refresh(
                RefreshRequest.newBuilder().setRefreshToken("bad").build(),
                refreshObserver
        );

        ArgumentCaptor<Throwable> captor = ArgumentCaptor.forClass(Throwable.class);
        verify(refreshObserver).onError(captor.capture());
        StatusRuntimeException sre = (StatusRuntimeException) captor.getValue();
        assertEquals(Status.Code.UNAUTHENTICATED, sre.getStatus().getCode());
        assertEquals("INVALID_REFRESH_TOKEN", sre.getTrailers().get(GrpcStatusMapper.ERROR_CODE_KEY));
    }
}
