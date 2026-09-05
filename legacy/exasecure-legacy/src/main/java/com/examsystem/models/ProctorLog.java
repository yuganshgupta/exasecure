package com.examsystem.models;
import java.sql.Timestamp;

public class ProctorLog {
    private final String message;
    private final Timestamp timestamp;
    private final byte[] screenshotData;

    public ProctorLog(String message, Timestamp timestamp, byte[] screenshotData) {
        this.message = message;
        this.timestamp = timestamp;
        this.screenshotData = screenshotData;
    }
    public String getMessage() { return message; }
    public Timestamp getTimestamp() { return timestamp; }
    public byte[] getScreenshotData() { return screenshotData; }
}