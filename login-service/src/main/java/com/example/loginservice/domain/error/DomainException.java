package com.example.loginservice.domain.error;

public final class DomainException extends RuntimeException {

    private final ErrorCode code;

    public DomainException(ErrorCode code, String detail) {
        super(detail);
        this.code = code;
    }

    public ErrorCode getCode() {
        return code;
    }

    public String getDetail() {
        return getMessage();
    }
}
