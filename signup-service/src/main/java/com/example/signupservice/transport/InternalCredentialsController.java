package com.example.signupservice.transport;

import com.example.signupservice.domain.error.DomainException;
import com.example.signupservice.domain.error.ErrorCode;
import com.example.signupservice.domain.model.User;
import com.example.signupservice.domain.usecase.VerifyCredentialsService;
import com.example.signupservice.transport.dto.VerifyCredentialsRequest;
import com.example.signupservice.transport.dto.VerifyCredentialsResponse;
import com.example.signupservice.transport.mapper.SignupTransportMapper;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(path = "/internal/v1/credentials", produces = MediaType.APPLICATION_JSON_VALUE)
public class InternalCredentialsController {

    public static final String INTERNAL_API_KEY_HEADER = "X-Internal-Api-Key";

    private final VerifyCredentialsService verifyCredentialsService;
    private final SignupTransportMapper mapper;
    private final String internalApiKey;

    public InternalCredentialsController(
            VerifyCredentialsService verifyCredentialsService,
            SignupTransportMapper mapper,
            @Qualifier("internalApiKey") String internalApiKey
    ) {
        this.verifyCredentialsService = verifyCredentialsService;
        this.mapper = mapper;
        this.internalApiKey = internalApiKey;
    }

    @PostMapping(path = "/verify", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<VerifyCredentialsResponse> verify(
            @RequestHeader(value = INTERNAL_API_KEY_HEADER, required = false) String apiKey,
            @Valid @RequestBody VerifyCredentialsRequest request
    ) {
        if (apiKey == null || !internalApiKey.equals(apiKey)) {
            throw new DomainException(
                    ErrorCode.UNAUTHORIZED,
                    401,
                    "Unauthorized",
                    "Unauthorized"
            );
        }
        User user = verifyCredentialsService.verify(request.email(), request.password());
        return ResponseEntity.ok(mapper.toVerifyResponse(user));
    }
}
