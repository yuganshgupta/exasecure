package com.examsystem.application.ports;

import com.examsystem.domain.models.Exam;
import java.util.Optional;

public interface ExamRepository {
    Optional<Exam> findById(int id);
}
