package com.examsystem.models;
import java.sql.Timestamp;

public class ProctorLog {
    private final String message;
    private final Timestamp timestamp;

    public ProctorLog(String message, Timestamp timestamp) {
        this.message = message;
        this.timestamp = timestamp;
    }
    public String getMessage() { return message; }
    public Timestamp getTimestamp() { return timestamp; }
}