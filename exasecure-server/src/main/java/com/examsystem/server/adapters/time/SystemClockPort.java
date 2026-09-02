package com.examsystem.server.adapters.time;

import com.examsystem.application.ports.ClockPort;

import java.time.Instant;

public class SystemClockPort implements ClockPort {
    @Override
    public Instant now() {
        return Instant.now();
    }
}
