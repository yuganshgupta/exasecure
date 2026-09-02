package com.examsystem.domain.models;

public record User(
    int id, 
    String username, 
    String fullName, 
    String enrollmentNumber, 
    String section, 
    Role role
) {
    public enum Role {
        STUDENT, ADMIN;

        public static Role fromString(String role) {
            if ("admin".equalsIgnoreCase(role)) return ADMIN;
            return STUDENT;
        }
    }
}
