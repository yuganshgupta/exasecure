package com.examsystem.contracts;

public record ErrorResponseDto(int code, String message, String requestId) {}
