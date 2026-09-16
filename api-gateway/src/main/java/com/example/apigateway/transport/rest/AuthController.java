package com.example.apigateway.transport.rest;

import com.example.apigateway.infrastructure.LoginAuthClient;
import com.example.apigateway.infrastructure.SignupAuthClient;
import com.example.apigateway.transport.dto.LoginRequest;
import com.example.apigateway.transport.dto.RefreshRequest;
import com.example.apigateway.transport.dto.SignupRequest;
import com.example.apigateway.transport.dto.SignupResponse;
import com.example.apigateway.transport.dto.TokenResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(path = "/v1/auth", produces = MediaType.APPLICATION_JSON_VALUE)
public class AuthController {

    private final SignupAuthClient signupAuthClient;
    private final LoginAuthClient loginAuthClient;

    public AuthController(SignupAuthClient signupAuthClient, LoginAuthClient loginAuthClient) {
        this.signupAuthClient = signupAuthClient;
        this.loginAuthClient = loginAuthClient;
    }

    @PostMapping(path = "/signup", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<SignupResponse> signup(
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody SignupRequest request
    ) {
        IdempotencyKeys.requireValid(idempotencyKey);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(signupAuthClient.register(request, idempotencyKey));
    }

    @PostMapping(path = "/login", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<TokenResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(loginAuthClient.login(request));
    }

    @PostMapping(path = "/refresh", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<TokenResponse> refresh(@Valid @RequestBody RefreshRequest request) {
        return ResponseEntity.ok(loginAuthClient.refresh(request));
    }
}
