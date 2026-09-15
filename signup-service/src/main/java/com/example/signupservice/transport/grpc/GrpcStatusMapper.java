package com.example.signupservice.transport.grpc;

import com.example.signupservice.domain.error.DomainException;
import io.grpc.Metadata;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;

final class GrpcStatusMapper {

    static final Metadata.Key<String> ERROR_CODE_KEY =
            Metadata.Key.of("error-code", Metadata.ASCII_STRING_MARSHALLER);

    private GrpcStatusMapper() {
    }

    static StatusRuntimeException toStatus(DomainException ex) {
        Status status = switch (ex.getCode()) {
            case EMAIL_REQUIRED, PASSWORD_REQUIRED, EMAIL_INVALID,
                 PASSWORD_TOO_SHORT, PASSWORD_TOO_LONG -> Status.INVALID_ARGUMENT;
            case EMAIL_TAKEN -> Status.ALREADY_EXISTS;
            case INVALID_CREDENTIALS, UNAUTHORIZED -> Status.UNAUTHENTICATED;
        };
        Metadata trailers = new Metadata();
        trailers.put(ERROR_CODE_KEY, ex.getCode().name());
        return status.withDescription(ex.getDetail()).asRuntimeException(trailers);
    }

    static StatusRuntimeException internal(Throwable cause) {
        Metadata trailers = new Metadata();
        trailers.put(ERROR_CODE_KEY, "INTERNAL");
        return Status.INTERNAL.withDescription("Internal error").withCause(cause).asRuntimeException(trailers);
    }
}
