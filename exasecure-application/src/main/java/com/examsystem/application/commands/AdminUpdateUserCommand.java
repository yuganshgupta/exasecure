package com.examsystem.application.commands;

import com.examsystem.domain.models.User;

public record AdminUpdateUserCommand(
        int id,
        String username,
        String password,
        String fullName,
        String enrollmentNumber,
        String section,
        User.Role role
) {}
