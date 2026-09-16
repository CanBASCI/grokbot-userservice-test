package com.example.signupservice.transport.grpc;

import com.example.auth.signup.v1.RegisterRequest;
import com.example.auth.signup.v1.RegisterResponse;
import com.example.auth.signup.v1.SignupServiceGrpc;
import com.example.auth.signup.v1.VerifyCredentialsRequest;
import com.example.auth.signup.v1.VerifyCredentialsResponse;
import com.example.signupservice.domain.error.DomainException;
import com.example.signupservice.domain.error.ErrorCode;
import com.example.signupservice.domain.model.User;
import com.example.signupservice.domain.port.SignupIdempotencyStore;
import com.example.signupservice.domain.usecase.SignupService;
import com.example.signupservice.domain.usecase.VerifyCredentialsService;
import com.example.signupservice.infrastructure.RequestFingerprint;
import io.grpc.stub.StreamObserver;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.grpc.server.service.GrpcService;
import org.springframework.transaction.annotation.Transactional;

@GrpcService
public class SignupGrpcService extends SignupServiceGrpc.SignupServiceImplBase {

    private final SignupService signupService;
    private final VerifyCredentialsService verifyCredentialsService;
    private final SignupIdempotencyStore idempotencyStore;

    public SignupGrpcService(
            SignupService signupService,
            VerifyCredentialsService verifyCredentialsService,
            SignupIdempotencyStore idempotencyStore
    ) {
        this.signupService = signupService;
        this.verifyCredentialsService = verifyCredentialsService;
        this.idempotencyStore = idempotencyStore;
    }

    @Override
    @Transactional
    public void register(RegisterRequest request, StreamObserver<RegisterResponse> responseObserver) {
        try {
            String idempotencyKey = requireIdempotencyKey();
            String fingerprint = RequestFingerprint.ofSignup(request.getEmail(), request.getPassword());

            var existing = idempotencyStore.find(idempotencyKey);
            if (existing.isPresent()) {
                SignupIdempotencyStore.Record record = existing.get();
                if (!record.requestHash().equals(fingerprint)) {
                    throw new DomainException(
                            ErrorCode.IDEMPOTENCY_KEY_BODY_MISMATCH,
                            "Idempotency key was reused with a different request body"
                    );
                }
                RegisterResponse replay = RegisterResponse.newBuilder()
                        .setUserId(record.userId().toString())
                        .setEmail(record.email())
                        .build();
                responseObserver.onNext(replay);
                responseObserver.onCompleted();
                return;
            }

            User user;
            try {
                user = signupService.signup(request.getEmail(), request.getPassword());
            } catch (DataIntegrityViolationException ex) {
                throw new DomainException(ErrorCode.EMAIL_TAKEN, "Email is already registered");
            }

            try {
                idempotencyStore.save(idempotencyKey, fingerprint, user.getId(), user.getEmail());
            } catch (DataIntegrityViolationException race) {
                SignupIdempotencyStore.Record record = idempotencyStore.find(idempotencyKey)
                        .orElseThrow(() -> race);
                if (!record.requestHash().equals(fingerprint)) {
                    throw new DomainException(
                            ErrorCode.IDEMPOTENCY_KEY_BODY_MISMATCH,
                            "Idempotency key was reused with a different request body"
                    );
                }
                RegisterResponse replay = RegisterResponse.newBuilder()
                        .setUserId(record.userId().toString())
                        .setEmail(record.email())
                        .build();
                responseObserver.onNext(replay);
                responseObserver.onCompleted();
                return;
            }

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

    private static String requireIdempotencyKey() {
        String key = IdempotencyKeyInterceptor.IDEMPOTENCY_KEY.get();
        if (key == null || key.isBlank()) {
            throw new DomainException(ErrorCode.IDEMPOTENCY_KEY_REQUIRED, "Idempotency-Key is required");
        }
        if (key.length() > 128) {
            throw new DomainException(ErrorCode.IDEMPOTENCY_KEY_INVALID, "Idempotency-Key is too long");
        }
        for (int i = 0; i < key.length(); i++) {
            char c = key.charAt(i);
            if (c < 0x20 || c > 0x7E) {
                throw new DomainException(
                        ErrorCode.IDEMPOTENCY_KEY_INVALID,
                        "Idempotency-Key must be printable ASCII"
                );
            }
        }
        return key;
    }
}
