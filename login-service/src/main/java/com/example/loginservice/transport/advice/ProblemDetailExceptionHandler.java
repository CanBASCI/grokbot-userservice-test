package com.example.loginservice.transport.advice;

import com.example.loginservice.domain.error.DomainException;
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

    private static final Set<String> KNOWN_CODES = Set.of(
            "EMAIL_REQUIRED",
            "PASSWORD_REQUIRED",
            "REFRESH_TOKEN_REQUIRED"
    );

    @ExceptionHandler(DomainException.class)
    public ResponseEntity<ProblemDetail> handleDomain(DomainException ex) {
        return problem(ex.getStatus(), ex.getTitle(), ex.getDetail(), ex.getCode().name());
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
                case "PASSWORD_REQUIRED" -> "Password is required";
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
