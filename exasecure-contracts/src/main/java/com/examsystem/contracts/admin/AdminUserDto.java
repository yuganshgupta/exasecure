package com.examsystem.contracts.admin;

public record AdminUserDto(
        int id,
        String username,
        String fullName,
        String role,
        boolean isActive,
        String enrollmentNumber,
        String section
) {
    public AdminUserDto(int id, String username, String fullName, String role, boolean isActive) {
        this(id, username, fullName, role, isActive, null, null);
    }
}
