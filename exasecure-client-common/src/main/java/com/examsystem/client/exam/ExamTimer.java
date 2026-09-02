package com.examsystem.client.exam;

import java.time.Duration;
import java.time.Instant;

public class ExamTimer {
    private final Instant startTime;
    private final int durationMinutes;
    private final Instant expiryTime;

    public ExamTimer(Instant startTime, int durationMinutes) {
        if (startTime == null) {
            throw new IllegalArgumentException("Start time cannot be null");
        }
        if (durationMinutes <= 0) {
            throw new IllegalArgumentException("Duration must be positive");
        }
        this.startTime = startTime;
        this.durationMinutes = durationMinutes;
        this.expiryTime = startTime.plus(Duration.ofMinutes(durationMinutes));
    }

    public Instant getStartTime() {
        return startTime;
    }

    public int getDurationMinutes() {
        return durationMinutes;
    }

    public Instant getExpiryTime() {
        return expiryTime;
    }

    public long getRemainingSeconds() {
        return getRemainingSecondsAt(Instant.now());
    }

    public long getRemainingSecondsAt(Instant currentTime) {
        if (currentTime.isAfter(expiryTime)) {
            return 0;
        }
        return Duration.between(currentTime, expiryTime).toSeconds();
    }

    public boolean isExpired() {
        return isExpiredAt(Instant.now());
    }

    public boolean isExpiredAt(Instant currentTime) {
        return !currentTime.isBefore(expiryTime);
    }

    public static String formatRemainingTime(long seconds) {
        if (seconds <= 0) {
            return "00:00";
        }
        long mins = seconds / 60;
        long secs = seconds % 60;
        return String.format("%02d:%02d", mins, secs);
    }
}
