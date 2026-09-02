package com.examsystem.application.query;

public record AdminUserResult(
        int id,
        String username,
        String fullName,
        String role,
        boolean active,
        String enrollmentNumber,
        String section
) {
    public AdminUserResult(int id, String username, String fullName, String role, boolean active) {
        this(id, username, fullName, role, active, null, null);
    }
}
