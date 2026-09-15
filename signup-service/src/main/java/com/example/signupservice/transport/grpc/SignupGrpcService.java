package com.example.signupservice.transport.grpc;

import com.example.auth.signup.v1.RegisterRequest;
import com.example.auth.signup.v1.RegisterResponse;
import com.example.auth.signup.v1.SignupServiceGrpc;
import com.example.auth.signup.v1.VerifyCredentialsRequest;
import com.example.auth.signup.v1.VerifyCredentialsResponse;
import com.example.signupservice.domain.error.DomainException;
import com.example.signupservice.domain.model.User;
import com.example.signupservice.domain.usecase.SignupService;
import com.example.signupservice.domain.usecase.VerifyCredentialsService;
import io.grpc.stub.StreamObserver;
import org.springframework.grpc.server.service.GrpcService;

@GrpcService
public class SignupGrpcService extends SignupServiceGrpc.SignupServiceImplBase {

    private final SignupService signupService;
    private final VerifyCredentialsService verifyCredentialsService;

    public SignupGrpcService(
            SignupService signupService,
            VerifyCredentialsService verifyCredentialsService
    ) {
        this.signupService = signupService;
        this.verifyCredentialsService = verifyCredentialsService;
    }

    @Override
    public void register(RegisterRequest request, StreamObserver<RegisterResponse> responseObserver) {
        try {
            User user = signupService.signup(request.getEmail(), request.getPassword());
            RegisterResponse response = RegisterResponse.newBuilder()
                    .setUserId(user.getId().toString())
                    .setEmail(user.getEmail())
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
    public void verifyCredentials(
            VerifyCredentialsRequest request,
            StreamObserver<VerifyCredentialsResponse> responseObserver
    ) {
        try {
            User user = verifyCredentialsService.verify(request.getEmail(), request.getPassword());
            VerifyCredentialsResponse response = VerifyCredentialsResponse.newBuilder()
                    .setUserId(user.getId().toString())
                    .setEmail(user.getEmail())
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
