package com.examsystem.contracts;

public record AuthResponseDto(String token, String role, String fullName) {}
