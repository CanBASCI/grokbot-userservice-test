package com.example.loginservice.domain.error;

public final class DomainException extends RuntimeException {

    private final ErrorCode code;
    private final int status;
    private final String title;

    public DomainException(ErrorCode code, int status, String title, String detail) {
        super(detail);
        this.code = code;
        this.status = status;
        this.title = title;
    }

    public ErrorCode getCode() {
        return code;
    }

    public int getStatus() {
        return status;
    }

    public String getTitle() {
        return title;
    }

    public String getDetail() {
        return getMessage();
    }
}
