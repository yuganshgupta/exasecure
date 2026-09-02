package com.examsystem.application.ports;

import com.examsystem.domain.models.Question;
import java.util.List;
import java.util.Optional;

public interface QuestionRepository {
    Optional<Question> findById(int id);
    List<Question> findByExamId(int examId);
}
