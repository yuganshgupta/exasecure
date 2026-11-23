package com.examsystem.application.ports;

public final class DeadlineNotReachedException extends IllegalStateException {
    public DeadlineNotReachedException() {
        super("Exam deadline has not been reached");
    }
}
