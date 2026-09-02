package com.examsystem.contracts;

import java.util.List;

public record StudentSaveAnswerRequestDto(int questionId, List<Integer> selectedOptions) {}
