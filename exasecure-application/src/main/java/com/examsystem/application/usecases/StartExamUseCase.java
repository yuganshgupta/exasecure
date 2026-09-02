package com.examsystem.application.usecases;

import com.examsystem.application.ports.ClockPort;
import com.examsystem.application.ports.ExamAttemptRepository;
import com.examsystem.domain.models.ExamAttempt;


public class StartExamUseCase {
    private final ExamAttemptRepository attemptRepository;
    private final ClockPort clockPort;

    public StartExamUseCase(ExamAttemptRepository attemptRepository, ClockPort clockPort) {
        this.attemptRepository = attemptRepository;
        this.clockPort = clockPort;
    }

    public ExamAttempt startOrResume(int studentId, int examId, java.time.Duration timeLimit) {
        // Here we just fetch or create. 
        // In this implementation, we delegate to repository to handle start/resume logic safely.
        return attemptRepository.startOrResumeAttempt(studentId, examId, clockPort.now(), timeLimit);
    }
}
