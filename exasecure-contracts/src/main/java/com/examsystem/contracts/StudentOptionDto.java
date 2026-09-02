package com.examsystem.contracts;

/**
 * A safe projection of an option for the student client.
 * Does not contain the 'isCorrect' boolean.
 */
public record StudentOptionDto(
    int id,
    String text
) {}
