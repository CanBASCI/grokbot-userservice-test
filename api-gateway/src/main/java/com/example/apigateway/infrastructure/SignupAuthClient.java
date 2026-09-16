package com.example.apigateway.infrastructure;

import com.example.apigateway.transport.dto.SignupRequest;
import com.example.apigateway.transport.dto.SignupResponse;
import com.example.auth.signup.v1.RegisterRequest;
import com.example.auth.signup.v1.RegisterResponse;
import com.example.auth.signup.v1.SignupServiceGrpc;
import io.grpc.Metadata;
import io.grpc.stub.MetadataUtils;
import org.springframework.stereotype.Component;

@Component
public class SignupAuthClient {

    public static final Metadata.Key<String> IDEMPOTENCY_KEY =
            Metadata.Key.of("idempotency-key", Metadata.ASCII_STRING_MARSHALLER);

    private final SignupServiceGrpc.SignupServiceBlockingStub signupStub;

    public SignupAuthClient(SignupServiceGrpc.SignupServiceBlockingStub signupStub) {
        this.signupStub = signupStub;
    }

    public SignupResponse register(SignupRequest request, String idempotencyKey) {
        Metadata headers = new Metadata();
        headers.put(IDEMPOTENCY_KEY, idempotencyKey);
        RegisterResponse response = signupStub
                .withInterceptors(MetadataUtils.newAttachHeadersInterceptor(headers))
                .register(RegisterRequest.newBuilder()
                        .setEmail(request.email())
                        .setPassword(request.password())
                        .build());
        return new SignupResponse(response.getUserId(), response.getEmail());
    }
}
