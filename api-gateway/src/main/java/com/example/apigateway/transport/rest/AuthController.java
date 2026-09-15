package com.example.apigateway.transport.rest;

import com.example.apigateway.transport.dto.LoginRequest;
import com.example.apigateway.transport.dto.RefreshRequest;
import com.example.apigateway.transport.dto.SignupRequest;
import com.example.apigateway.transport.dto.SignupResponse;
import com.example.apigateway.transport.dto.TokenResponse;
import com.example.auth.login.v1.LoginServiceGrpc;
import com.example.auth.login.v1.LoginResponse;
import com.example.auth.login.v1.RefreshResponse;
import com.example.auth.signup.v1.RegisterRequest;
import com.example.auth.signup.v1.RegisterResponse;
import com.example.auth.signup.v1.SignupServiceGrpc;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(path = "/v1/auth", produces = MediaType.APPLICATION_JSON_VALUE)
public class AuthController {

    private final SignupServiceGrpc.SignupServiceBlockingStub signupStub;
    private final LoginServiceGrpc.LoginServiceBlockingStub loginStub;

    public AuthController(
            SignupServiceGrpc.SignupServiceBlockingStub signupStub,
            LoginServiceGrpc.LoginServiceBlockingStub loginStub
    ) {
        this.signupStub = signupStub;
        this.loginStub = loginStub;
    }

    @PostMapping(path = "/signup", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<SignupResponse> signup(@Valid @RequestBody SignupRequest request) {
        RegisterResponse response = signupStub.register(
                RegisterRequest.newBuilder()
                        .setEmail(request.email())
                        .setPassword(request.password())
                        .build()
        );
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new SignupResponse(response.getUserId(), response.getEmail()));
    }

    @PostMapping(path = "/login", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<TokenResponse> login(@Valid @RequestBody LoginRequest request) {
        LoginResponse response = loginStub.login(
                com.example.auth.login.v1.LoginRequest.newBuilder()
                        .setEmail(request.email())
                        .setPassword(request.password())
                        .build()
        );
        return ResponseEntity.ok(new TokenResponse(
                response.getAccessToken(),
                response.getRefreshToken(),
                response.getTokenType(),
                response.getExpiresIn()
        ));
    }

    @PostMapping(path = "/refresh", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<TokenResponse> refresh(@Valid @RequestBody RefreshRequest request) {
        RefreshResponse response = loginStub.refresh(
                com.example.auth.login.v1.RefreshRequest.newBuilder()
                        .setRefreshToken(request.refreshToken())
                        .build()
        );
        return ResponseEntity.ok(new TokenResponse(
                response.getAccessToken(),
                response.getRefreshToken(),
                response.getTokenType(),
                response.getExpiresIn()
        ));
    }
}
