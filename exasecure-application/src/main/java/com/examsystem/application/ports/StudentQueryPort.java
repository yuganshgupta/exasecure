package com.examsystem.application.ports;

import com.examsystem.application.query.StudentAttemptHistoryResult;
import com.examsystem.application.query.StudentExamResult;
import java.util.List;

public interface StudentQueryPort {
    List<StudentAttemptHistoryResult> listHistory(int studentId);
    List<StudentExamResult> listAvailableExams();
}
