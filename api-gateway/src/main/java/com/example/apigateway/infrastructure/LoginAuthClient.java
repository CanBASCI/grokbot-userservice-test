package com.example.apigateway.infrastructure;

import com.example.apigateway.transport.dto.LoginRequest;
import com.example.apigateway.transport.dto.RefreshRequest;
import com.example.apigateway.transport.dto.TokenResponse;
import com.example.auth.login.v1.LoginResponse;
import com.example.auth.login.v1.LoginServiceGrpc;
import com.example.auth.login.v1.RefreshResponse;
import org.springframework.stereotype.Component;

@Component
public class LoginAuthClient {

    private final LoginServiceGrpc.LoginServiceBlockingStub loginStub;

    public LoginAuthClient(LoginServiceGrpc.LoginServiceBlockingStub loginStub) {
        this.loginStub = loginStub;
    }

    public TokenResponse login(LoginRequest request) {
        LoginResponse response = loginStub.login(
                com.example.auth.login.v1.LoginRequest.newBuilder()
                        .setEmail(request.email())
                        .setPassword(request.password())
                        .build()
        );
        return toTokenResponse(response.getAccessToken(), response.getRefreshToken(),
                response.getTokenType(), response.getExpiresIn());
    }

    public TokenResponse refresh(RefreshRequest request) {
        RefreshResponse response = loginStub.refresh(
                com.example.auth.login.v1.RefreshRequest.newBuilder()
                        .setRefreshToken(request.refreshToken())
                        .build()
        );
        return toTokenResponse(response.getAccessToken(), response.getRefreshToken(),
                response.getTokenType(), response.getExpiresIn());
    }

    private static TokenResponse toTokenResponse(
            String accessToken,
            String refreshToken,
            String tokenType,
            long expiresIn
    ) {
        return new TokenResponse(accessToken, refreshToken, tokenType, expiresIn);
    }
}
