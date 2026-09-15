package com.example.loginservice.transport.grpc;

import com.example.loginservice.domain.error.DomainException;
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
            case EMAIL_REQUIRED, PASSWORD_REQUIRED, REFRESH_TOKEN_REQUIRED -> Status.INVALID_ARGUMENT;
            case INVALID_CREDENTIALS, INVALID_REFRESH_TOKEN -> Status.UNAUTHENTICATED;
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
