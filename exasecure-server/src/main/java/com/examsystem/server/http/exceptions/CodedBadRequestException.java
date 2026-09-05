package com.examsystem.server.http.exceptions;

/** A bad request with an optional machine-readable recovery reason. */
public final class CodedBadRequestException extends IllegalArgumentException {
    private final String errorCode;

    public CodedBadRequestException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public String getErrorCode() { return errorCode; }
}
