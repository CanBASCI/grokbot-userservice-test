package com.example.apigateway.transport.advice;

import io.grpc.Metadata;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import tools.jackson.databind.exc.UnrecognizedPropertyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.net.URI;
import java.util.Set;

@RestControllerAdvice
public class ProblemDetailExceptionHandler {

    public static final Metadata.Key<String> ERROR_CODE_KEY =
            Metadata.Key.of("error-code", Metadata.ASCII_STRING_MARSHALLER);

    private static final Set<String> KNOWN_CODES = Set.of(
            "EMAIL_REQUIRED",
            "PASSWORD_REQUIRED",
            "EMAIL_INVALID",
            "PASSWORD_TOO_SHORT",
            "PASSWORD_TOO_LONG",
            "REFRESH_TOKEN_REQUIRED"
    );

    @ExceptionHandler(StatusRuntimeException.class)
    public ResponseEntity<ProblemDetail> handleGrpc(StatusRuntimeException ex) {
        Status.Code code = ex.getStatus().getCode();
        String errorCode = extractErrorCode(ex);
        String detail = ex.getStatus().getDescription() != null
                ? ex.getStatus().getDescription()
                : "Request failed";

        return switch (code) {
            case INVALID_ARGUMENT -> problem(400, "Bad Request", detail,
                    errorCode != null ? errorCode : "INVALID_REQUEST");
            case ALREADY_EXISTS -> problem(409, "Conflict", detail,
                    errorCode != null ? errorCode : "EMAIL_TAKEN");
            case UNAUTHENTICATED -> problem(401, "Unauthorized", detail,
                    errorCode != null ? errorCode : "UNAUTHORIZED");
            default -> problem(500, "Internal Server Error", "Internal error",
                    errorCode != null ? errorCode : "INTERNAL");
        };
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
        String code = "INVALID_REQUEST";
        String detail = "Request body is invalid";
        if (cause instanceof UnrecognizedPropertyException upe) {
            code = "UNKNOWN_PROPERTY";
            detail = "Unknown property: " + upe.getPropertyName();
        }
        return problem(400, "Bad Request", detail, code);
    }

    private static String extractErrorCode(StatusRuntimeException ex) {
        Metadata trailers = ex.getTrailers();
        if (trailers == null) {
            return null;
        }
        return trailers.get(ERROR_CODE_KEY);
    }

    private static ResponseEntity<ProblemDetail> problem(int status, String title, String detail, String code) {
        ProblemDetail body = ProblemDetail.forStatusAndDetail(HttpStatus.valueOf(status), detail);
        body.setTitle(title);
        body.setType(URI.create("about:blank"));
        body.setProperty("code", code);
        return ResponseEntity.status(status)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(body);
    }
}
