package com.examsystem.contracts;

public record ErrorResponseDto(int code, String message, String requestId, String errorCode) {
    public ErrorResponseDto(int code, String message, String requestId) {
        this(code, message, requestId, null);
    }
}
