package com.examsystem.application.usecases;

import com.examsystem.application.commands.AdminCreateUserCommand;
import com.examsystem.application.commands.AdminUpdateUserCommand;
import com.examsystem.application.ports.AdminUserMutationPort;
import com.examsystem.application.ports.PasswordEncoder;

import java.nio.charset.StandardCharsets;

public class AdminUserMutationUseCase {
    private final AdminUserMutationPort mutationPort;
    private final PasswordEncoder passwordEncoder;

    public AdminUserMutationUseCase(AdminUserMutationPort mutationPort, PasswordEncoder passwordEncoder) {
        this.mutationPort = mutationPort;
        this.passwordEncoder = passwordEncoder;
    }

    public int createUser(AdminCreateUserCommand command) {
        if (command == null) {
            throw new IllegalArgumentException("User is required");
        }
        String password = requirePassword(command.password());
        return mutationPort.createUser(
                requireText(command.username(), "Username", 50),
                passwordEncoder.encode(password),
                requireText(command.fullName(), "Full name", 100),
                optionalText(command.enrollmentNumber(), "Enrollment number", 50),
                optionalText(command.section(), "Section", 10),
                requireRole(command.role()));
    }

    public boolean updateUser(AdminUpdateUserCommand command) {
        if (command == null) {
            throw new IllegalArgumentException("User is required");
        }
        requirePositiveId(command.id());
        String encodedPassword = null;
        if (command.password() != null && !command.password().isBlank()) {
            encodedPassword = passwordEncoder.encode(requirePassword(command.password()));
        }
        return mutationPort.updateUser(
                command.id(),
                requireText(command.username(), "Username", 50),
                encodedPassword,
                requireText(command.fullName(), "Full name", 100),
                optionalText(command.enrollmentNumber(), "Enrollment number", 50),
                optionalText(command.section(), "Section", 10),
                requireRole(command.role()));
    }

    public boolean deactivateUser(int id) {
        requirePositiveId(id);
        return mutationPort.deactivateUser(id);
    }

    public boolean reactivateUser(int id) {
        requirePositiveId(id);
        return mutationPort.reactivateUser(id);
    }

    public boolean deleteUser(int id) {
        requirePositiveId(id);
        return mutationPort.deleteUser(id);
    }

    private static String requirePassword(String password) {
        if (password == null || password.length() < 8) {
            throw new IllegalArgumentException("Password must be at least 8 characters");
        }
        if (password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new IllegalArgumentException("Password must be at most 72 UTF-8 bytes");
        }
        return password;
    }

    private static String requireText(String value, String label, int maxLength) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(label + " is required");
        }
        String normalized = value.trim();
        if (normalized.length() > maxLength) {
            throw new IllegalArgumentException(label + " must be at most " + maxLength + " characters");
        }
        return normalized;
    }

    private static String optionalText(String value, String label, int maxLength) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.length() > maxLength) {
            throw new IllegalArgumentException(label + " must be at most " + maxLength + " characters");
        }
        return normalized;
    }

    private static com.examsystem.domain.models.User.Role requireRole(
            com.examsystem.domain.models.User.Role role) {
        if (role == null) {
            throw new IllegalArgumentException("Role is required");
        }
        return role;
    }

    private static void requirePositiveId(int id) {
        if (id <= 0) {
            throw new IllegalArgumentException("User ID must be positive");
        }
    }
}
