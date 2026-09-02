package com.examsystem.application.ports;

import com.examsystem.domain.models.User;
import java.util.Optional;

public interface UserRepository {
    Optional<User> findByUsername(String username);
    Optional<String> getPasswordHash(String username);
}
