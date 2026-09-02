package com.examsystem.application.ports;

import com.examsystem.contracts.StudentAttemptHistoryDto;
import java.util.List;

public interface StudentQueryPort {
    List<StudentAttemptHistoryDto> listHistory(int studentId);
}
