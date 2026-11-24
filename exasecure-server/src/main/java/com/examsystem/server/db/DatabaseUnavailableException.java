package com.examsystem.server.db;

import java.sql.SQLException;

/** Infrastructure failure with a safe public message and a fixed diagnostic operation. */
public final class DatabaseUnavailableException extends RuntimeException {
    private final String operation;

    public DatabaseUnavailableException(String operation, SQLException cause) {
        super("Database temporarily unavailable. Try again when the Server is ready.", cause);
        this.operation = operation;
    }

    public String operation() {
        return operation;
    }
}
