package com.examsystem.application.ports;

import java.time.Instant;

public interface ClockPort {
    Instant now();
}
