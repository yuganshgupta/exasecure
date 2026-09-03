package com.examsystem.application.ports;

import com.examsystem.domain.models.User;

public interface AdminUserMutationPort {
    int createUser(
            String username,
            String encodedPassword,
            String fullName,
            String enrollmentNumber,
            String section,
            User.Role role);

    boolean updateUser(
            int id,
            String username,
            String encodedPassword,
            String fullName,
            String enrollmentNumber,
            String section,
            User.Role role);

    boolean deactivateUser(int id);

    boolean reactivateUser(int id);
}
