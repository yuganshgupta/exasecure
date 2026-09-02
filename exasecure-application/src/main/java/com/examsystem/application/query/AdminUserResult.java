package com.examsystem.application.query;

public record AdminUserResult(
        int id,
        String username,
        String fullName,
        String role,
        boolean active
) {}
