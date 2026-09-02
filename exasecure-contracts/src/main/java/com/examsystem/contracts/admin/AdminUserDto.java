package com.examsystem.contracts.admin;

public record AdminUserDto(int id, String username, String fullName, String role, boolean isActive) {}
