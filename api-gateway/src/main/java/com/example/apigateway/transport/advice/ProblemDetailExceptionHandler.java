package com.example.apigateway.transport.advice;

import com.google.protobuf.Any;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.rpc.ErrorInfo;
import com.google.rpc.Status;
import io.grpc.Metadata;
import io.grpc.StatusRuntimeException;
import io.grpc.protobuf.StatusProto;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.MDC;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import tools.jackson.databind.exc.UnrecognizedPropertyException;

import java.net.URI;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@RestControllerAdvice
public class ProblemDetailExceptionHandler {

    public static final Metadata.Key<String> ERROR_CODE_KEY =
            Metadata.Key.of("error-code", Metadata.ASCII_STRING_MARSHALLER);

    private static final String FIXED_5XX_DETAIL = "An unexpected error occurred.";

    private static final Set<String> KNOWN_CODES = Set.of(
            "EMAIL_REQUIRED",
            "PASSWORD_REQUIRED",
            "EMAIL_INVALID",
            "PASSWORD_TOO_SHORT",
            "PASSWORD_TOO_LONG",
            "REFRESH_TOKEN_REQUIRED",
            "IDEMPOTENCY_KEY_REQUIRED",
            "IDEMPOTENCY_KEY_INVALID"
    );

    private final ObjectProvider<Tracer> tracer;

    public ProblemDetailExceptionHandler(ObjectProvider<Tracer> tracer) {
        this.tracer = tracer;
    }

    @ExceptionHandler(StatusRuntimeException.class)
    public ResponseEntity<ProblemDetail> handleGrpc(StatusRuntimeException ex, HttpServletRequest request) {
        io.grpc.Status.Code code = ex.getStatus().getCode();
        String errorCode = extractErrorCode(ex);
        String detail = ex.getStatus().getDescription() != null
                ? ex.getStatus().getDescription()
                : "Request failed";

        return switch (code) {
            case INVALID_ARGUMENT -> problem(400, "Bad Request", detail,
                    errorCode != null ? errorCode : "INVALID_REQUEST");
            case ALREADY_EXISTS -> problem(409, "Conflict", detail,
                    errorCode != null ? errorCode : "EMAIL_TAKEN");
            case UNAUTHENTICATED -> {
                String fallback = unauthenticatedFallback(request);
                yield problem(401, "Unauthorized", detail,
                        errorCode != null ? errorCode : fallback);
            }
            default -> problem(500, "Internal Server Error", FIXED_5XX_DETAIL,
                    errorCode != null ? errorCode : "INTERNAL");
        };
    }

    @ExceptionHandler(GatewayException.class)
    public ResponseEntity<ProblemDetail> handleGateway(GatewayException ex) {
        return problem(ex.getStatus(), ex.getTitle(), ex.getDetail(), ex.getCode());
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<ProblemDetail> handleMissingHeader(MissingRequestHeaderException ex) {
        if ("Idempotency-Key".equalsIgnoreCase(ex.getHeaderName())) {
            return problem(400, "Bad Request", "Idempotency-Key is required", "IDEMPOTENCY_KEY_REQUIRED");
        }
        return problem(400, "Bad Request", "Missing required header", "INVALID_REQUEST");
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ProblemDetail> handleValidation(MethodArgumentNotValidException ex) {
        FieldError fieldError = ex.getBindingResult().getFieldError();
        String code = "INVALID_REQUEST";
        String detail = "Request is invalid";
        if (fieldError != null) {
            String message = fieldError.getDefaultMessage();
            if (message != null && KNOWN_CODES.contains(message)) {
                code = message;
            } else if ("email".equals(fieldError.getField())) {
                code = "EMAIL_REQUIRED";
            } else if ("password".equals(fieldError.getField())) {
                code = "PASSWORD_REQUIRED";
            } else if ("refreshToken".equals(fieldError.getField())) {
                code = "REFRESH_TOKEN_REQUIRED";
            }
            detail = switch (code) {
                case "EMAIL_REQUIRED" -> "Email is required";
                case "EMAIL_INVALID" -> "Email is invalid";
                case "PASSWORD_REQUIRED" -> "Password is required";
                case "PASSWORD_TOO_SHORT" -> "Password is too short";
                case "PASSWORD_TOO_LONG" -> "Password is too long";
                case "REFRESH_TOKEN_REQUIRED" -> "Refresh token is required";
                default -> detail;
            };
        }
        return problem(400, "Bad Request", detail, code);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ProblemDetail> handleUnreadable(HttpMessageNotReadableException ex) {
        Throwable cause = ex.getMostSpecificCause();
        String code = "INVALID_JSON";
        String detail = "Request body is invalid JSON";
        if (cause instanceof UnrecognizedPropertyException upe) {
            code = "UNKNOWN_PROPERTY";
            detail = "Unknown property: " + upe.getPropertyName();
        }
        return problem(400, "Bad Request", detail, code);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> handleGeneric(Exception ex) {
        return problem(500, "Internal Server Error", FIXED_5XX_DETAIL, "INTERNAL");
    }

    static String extractErrorCode(StatusRuntimeException ex) {
        Status status = StatusProto.fromThrowable(ex);
        if (status != null) {
            for (Any detail : status.getDetailsList()) {
                if (detail.is(ErrorInfo.class)) {
                    try {
                        String reason = detail.unpack(ErrorInfo.class).getReason();
                        if (reason != null && !reason.isBlank()) {
                            return reason;
                        }
                    } catch (InvalidProtocolBufferException ignored) {
                        // fall through to trailer
                    }
                }
            }
        }
        Metadata trailers = ex.getTrailers();
        if (trailers == null) {
            return null;
        }
        return trailers.get(ERROR_CODE_KEY);
    }

    private static String unauthenticatedFallback(HttpServletRequest request) {
        String path = request != null ? request.getRequestURI() : "";
        if (path != null && path.contains("/refresh")) {
            return "INVALID_REFRESH_TOKEN";
        }
        return "INVALID_CREDENTIALS";
    }

    private ResponseEntity<ProblemDetail> problem(int status, String title, String detail, String code) {
        String effectiveDetail = status >= 500 ? FIXED_5XX_DETAIL : detail;
        ProblemDetail body = ProblemDetail.forStatusAndDetail(HttpStatus.valueOf(status), effectiveDetail);
        body.setTitle(title);
        body.setType(URI.create("https://grokbot.local/errors/" + toKebab(code)));
        body.setProperty("code", code);
        String traceId = resolveTraceId();
        body.setProperty("trace_id", traceId);
        MDC.put("trace_id", traceId);
        MDC.put("code", code);
        return ResponseEntity.status(status)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(body);
    }

    private String resolveTraceId() {
        Tracer t = tracer.getIfAvailable();
        if (t != null) {
            Span span = t.currentSpan();
            if (span != null && span.context() != null && span.context().traceId() != null) {
                return span.context().traceId();
            }
        }
        String fromMdc = MDC.get("traceId");
        if (fromMdc != null && !fromMdc.isBlank()) {
            return fromMdc;
        }
        fromMdc = MDC.get("trace_id");
        if (fromMdc != null && !fromMdc.isBlank()) {
            return fromMdc;
        }
        return UUID.randomUUID().toString().replace("-", "");
    }

    private static String toKebab(String code) {
        return code.toLowerCase(Locale.ROOT).replace('_', '-');
    }
}
