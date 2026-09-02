package com.examsystem.contracts;

import java.util.List;

/**
 * A safe projection of a question for the student client.
 * This contract intentionally omits correctness data to prevent client-side cheating.
 */
public record StudentQuestionDto(
    int id,
    int examId,
    String text,
    String type,
    List<StudentOptionDto> options
) {}
