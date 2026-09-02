package com.examsystem.client.api;

public class ApiException extends RuntimeException {
    private final int statusCode;
    private final String serverMessage;

    public ApiException(int statusCode, String serverMessage) {
        super(formatMessage(statusCode, serverMessage));
        this.statusCode = statusCode;
        this.serverMessage = serverMessage;
    }

    public ApiException(String message, Throwable cause) {
        super(message, cause);
        this.statusCode = -1;
        this.serverMessage = message;
    }

    private static String formatMessage(int statusCode, String serverMessage) {
        if (serverMessage == null || serverMessage.isBlank()) {
            return "Server returned HTTP status " + statusCode;
        }
        return serverMessage;
    }

    public int getStatusCode() {
        return statusCode;
    }

    public String getServerMessage() {
        return serverMessage;
    }

    public boolean isUnauthorized() {
        return statusCode == 401;
    }

    public boolean isForbidden() {
        return statusCode == 403;
    }

    public boolean isNotFound() {
        return statusCode == 404;
    }

    public boolean isConflict() {
        return statusCode == 409;
    }
}
