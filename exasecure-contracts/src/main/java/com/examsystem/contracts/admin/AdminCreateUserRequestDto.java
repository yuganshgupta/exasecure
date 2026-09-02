package com.examsystem.contracts.admin;

public record AdminCreateUserRequestDto(
        String username,
        String password,
        String fullName,
        String enrollmentNumber,
        String section,
        String role
) {}
