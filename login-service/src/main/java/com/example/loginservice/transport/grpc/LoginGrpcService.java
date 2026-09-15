package com.example.loginservice.transport.grpc;

import com.example.auth.login.v1.LoginRequest;
import com.example.auth.login.v1.LoginResponse;
import com.example.auth.login.v1.LoginServiceGrpc;
import com.example.auth.login.v1.RefreshRequest;
import com.example.auth.login.v1.RefreshResponse;
import com.example.loginservice.domain.error.DomainException;
import com.example.loginservice.domain.model.TokenPair;
import com.example.loginservice.domain.usecase.LoginService;
import com.example.loginservice.domain.usecase.RefreshService;
import io.grpc.stub.StreamObserver;
import org.springframework.grpc.server.service.GrpcService;

@GrpcService
public class LoginGrpcService extends LoginServiceGrpc.LoginServiceImplBase {

    private final LoginService loginService;
    private final RefreshService refreshService;

    public LoginGrpcService(LoginService loginService, RefreshService refreshService) {
        this.loginService = loginService;
        this.refreshService = refreshService;
    }

    @Override
    public void login(LoginRequest request, StreamObserver<LoginResponse> responseObserver) {
        try {
            TokenPair pair = loginService.login(request.getEmail(), request.getPassword());
            LoginResponse response = LoginResponse.newBuilder()
                    .setAccessToken(pair.getAccessToken())
                    .setRefreshToken(pair.getRefreshToken())
                    .setTokenType(pair.getTokenType())
                    .setExpiresIn(pair.getExpiresIn())
                    .build();
            responseObserver.onNext(response);
            responseObserver.onCompleted();
        } catch (DomainException ex) {
            responseObserver.onError(GrpcStatusMapper.toStatus(ex));
        } catch (RuntimeException ex) {
            responseObserver.onError(GrpcStatusMapper.internal(ex));
        }
    }

    @Override
    public void refresh(RefreshRequest request, StreamObserver<RefreshResponse> responseObserver) {
        try {
            TokenPair pair = refreshService.refresh(request.getRefreshToken());
            RefreshResponse response = RefreshResponse.newBuilder()
                    .setAccessToken(pair.getAccessToken())
                    .setRefreshToken(pair.getRefreshToken())
                    .setTokenType(pair.getTokenType())
                    .setExpiresIn(pair.getExpiresIn())
                    .build();
            responseObserver.onNext(response);
            responseObserver.onCompleted();
        } catch (DomainException ex) {
            responseObserver.onError(GrpcStatusMapper.toStatus(ex));
        } catch (RuntimeException ex) {
            responseObserver.onError(GrpcStatusMapper.internal(ex));
        }
    }
}
