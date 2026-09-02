package com.examsystem.contracts.admin;

public record AdminUpdateUserRequestDto(
        String username,
        String password,
        String fullName,
        String enrollmentNumber,
        String section,
        String role
) {}
