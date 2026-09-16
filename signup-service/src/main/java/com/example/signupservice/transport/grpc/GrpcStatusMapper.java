package com.example.signupservice.transport.grpc;

import com.example.signupservice.domain.error.DomainException;
import com.example.signupservice.domain.error.ErrorCode;
import com.google.protobuf.Any;
import com.google.rpc.ErrorInfo;
import com.google.rpc.Status;
import io.grpc.Metadata;
import io.grpc.StatusRuntimeException;
import io.grpc.protobuf.StatusProto;

final class GrpcStatusMapper {

    static final String DOMAIN = "auth.signup";

    static final Metadata.Key<String> ERROR_CODE_KEY =
            Metadata.Key.of("error-code", Metadata.ASCII_STRING_MARSHALLER);

    private GrpcStatusMapper() {
    }

    static StatusRuntimeException toStatus(DomainException ex) {
        io.grpc.Status.Code grpcCode = switch (ex.getCode()) {
            case EMAIL_REQUIRED, PASSWORD_REQUIRED, EMAIL_INVALID,
                 PASSWORD_TOO_SHORT, PASSWORD_TOO_LONG,
                 IDEMPOTENCY_KEY_REQUIRED, IDEMPOTENCY_KEY_INVALID -> io.grpc.Status.Code.INVALID_ARGUMENT;
            case EMAIL_TAKEN, IDEMPOTENCY_KEY_BODY_MISMATCH -> io.grpc.Status.Code.ALREADY_EXISTS;
            case INVALID_CREDENTIALS -> io.grpc.Status.Code.UNAUTHENTICATED;
            case INTERNAL -> io.grpc.Status.Code.INTERNAL;
        };
        return toStatusRuntimeException(grpcCode, ex.getCode().name(), ex.getDetail(), null);
    }

    static StatusRuntimeException internal(Throwable cause) {
        return toStatusRuntimeException(
                io.grpc.Status.Code.INTERNAL,
                ErrorCode.INTERNAL.name(),
                "Internal error",
                cause
        );
    }

    private static StatusRuntimeException toStatusRuntimeException(
            io.grpc.Status.Code grpcCode,
            String reason,
            String detail,
            Throwable cause
    ) {
        ErrorInfo errorInfo = ErrorInfo.newBuilder()
                .setReason(reason)
                .setDomain(DOMAIN)
                .build();
        Status.Builder statusBuilder = Status.newBuilder()
                .setCode(grpcCode.value())
                .setMessage(detail != null ? detail : "")
                .addDetails(Any.pack(errorInfo));
        Status statusProto = statusBuilder.build();
        Metadata trailers = new Metadata();
        trailers.put(ERROR_CODE_KEY, reason);
        StatusRuntimeException sre = StatusProto.toStatusRuntimeException(statusProto, trailers);
        if (cause != null) {
            return sre.getStatus().withCause(cause).asRuntimeException(sre.getTrailers());
        }
        return sre;
    }
}
