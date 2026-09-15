package com.example.signupservice.transport;

import com.example.signupservice.domain.model.User;
import com.example.signupservice.domain.usecase.SignupService;
import com.example.signupservice.transport.dto.SignupRequest;
import com.example.signupservice.transport.dto.SignupResponse;
import com.example.signupservice.transport.mapper.SignupTransportMapper;
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
public class SignupController {

    private final SignupService signupService;
    private final SignupTransportMapper mapper;

    public SignupController(SignupService signupService, SignupTransportMapper mapper) {
        this.signupService = signupService;
        this.mapper = mapper;
    }

    @PostMapping(path = "/signup", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<SignupResponse> signup(@Valid @RequestBody SignupRequest request) {
        User user = signupService.signup(request.email(), request.password());
        return ResponseEntity.status(HttpStatus.CREATED).body(mapper.toSignupResponse(user));
    }
}
