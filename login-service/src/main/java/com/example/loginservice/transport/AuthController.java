package com.example.loginservice.transport;

import com.example.loginservice.domain.model.TokenPair;
import com.example.loginservice.domain.usecase.LoginService;
import com.example.loginservice.domain.usecase.RefreshService;
import com.example.loginservice.transport.dto.LoginRequest;
import com.example.loginservice.transport.dto.RefreshRequest;
import com.example.loginservice.transport.dto.TokenResponse;
import com.example.loginservice.transport.mapper.LoginTransportMapper;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(path = "/v1/auth", produces = MediaType.APPLICATION_JSON_VALUE)
public class AuthController {

    private final LoginService loginService;
    private final RefreshService refreshService;
    private final LoginTransportMapper mapper;

    public AuthController(
            LoginService loginService,
            RefreshService refreshService,
            LoginTransportMapper mapper
    ) {
        this.loginService = loginService;
        this.refreshService = refreshService;
        this.mapper = mapper;
    }

    @PostMapping(path = "/login", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<TokenResponse> login(@RequestBody LoginRequest request) {
        TokenPair pair = loginService.login(request.getEmail(), request.getPassword());
        return ResponseEntity.ok(mapper.toResponse(pair));
    }

    @PostMapping(path = "/refresh", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<TokenResponse> refresh(@RequestBody RefreshRequest request) {
        TokenPair pair = refreshService.refresh(request.getRefreshToken());
        return ResponseEntity.ok(mapper.toResponse(pair));
    }
}
