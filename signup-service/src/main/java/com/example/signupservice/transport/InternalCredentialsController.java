package com.example.signupservice.transport;

import com.example.signupservice.domain.model.User;
import com.example.signupservice.domain.usecase.VerifyCredentialsService;
import com.example.signupservice.transport.dto.VerifyCredentialsRequest;
import com.example.signupservice.transport.dto.VerifyCredentialsResponse;
import com.example.signupservice.transport.mapper.SignupTransportMapper;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(path = "/internal/v1/credentials", produces = MediaType.APPLICATION_JSON_VALUE)
public class InternalCredentialsController {

    private final VerifyCredentialsService verifyCredentialsService;
    private final SignupTransportMapper mapper;

    public InternalCredentialsController(
            VerifyCredentialsService verifyCredentialsService,
            SignupTransportMapper mapper
    ) {
        this.verifyCredentialsService = verifyCredentialsService;
        this.mapper = mapper;
    }

    @PostMapping(path = "/verify", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<VerifyCredentialsResponse> verify(@RequestBody VerifyCredentialsRequest request) {
        User user = verifyCredentialsService.verify(request.getEmail(), request.getPassword());
        return ResponseEntity.ok(mapper.toVerifyResponse(user));
    }
}
