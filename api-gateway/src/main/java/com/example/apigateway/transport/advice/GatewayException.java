package com.example.apigateway.transport.advice;

public final class GatewayException extends RuntimeException {

    private final String code;
    private final int status;
    private final String title;

    public GatewayException(String code, int status, String title, String detail) {
        super(detail);
        this.code = code;
        this.status = status;
        this.title = title;
    }

    public String getCode() {
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
