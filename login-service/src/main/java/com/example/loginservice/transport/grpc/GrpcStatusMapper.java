package com.example.loginservice.transport.grpc;

import com.example.loginservice.domain.error.DomainException;
import com.example.loginservice.domain.error.ErrorCode;
import com.google.protobuf.Any;
import com.google.rpc.ErrorInfo;
import com.google.rpc.Status;
import io.grpc.Metadata;
import io.grpc.StatusRuntimeException;
import io.grpc.protobuf.StatusProto;

final class GrpcStatusMapper {

    static final String DOMAIN = "auth.login";

    static final Metadata.Key<String> ERROR_CODE_KEY =
            Metadata.Key.of("error-code", Metadata.ASCII_STRING_MARSHALLER);

    private GrpcStatusMapper() {
    }

    static StatusRuntimeException toStatus(DomainException ex) {
        io.grpc.Status.Code grpcCode = switch (ex.getCode()) {
            case EMAIL_REQUIRED, PASSWORD_REQUIRED, REFRESH_TOKEN_REQUIRED -> io.grpc.Status.Code.INVALID_ARGUMENT;
            case INVALID_CREDENTIALS, INVALID_REFRESH_TOKEN -> io.grpc.Status.Code.UNAUTHENTICATED;
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
        Status statusProto = Status.newBuilder()
                .setCode(grpcCode.value())
                .setMessage(detail != null ? detail : "")
                .addDetails(Any.pack(errorInfo))
                .build();
        Metadata trailers = new Metadata();
        trailers.put(ERROR_CODE_KEY, reason);
        StatusRuntimeException sre = StatusProto.toStatusRuntimeException(statusProto, trailers);
        if (cause != null) {
            return sre.getStatus().withCause(cause).asRuntimeException(sre.getTrailers());
        }
        return sre;
    }
}
