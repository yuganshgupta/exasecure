package com.examsystem.application.usecases;

import com.examsystem.application.ports.PasswordEncoder;
import com.examsystem.application.ports.UserRepository;
import com.examsystem.domain.models.User;

import java.util.Optional;

public class AuthenticateUserUseCase {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public AuthenticateUserUseCase(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public Optional<User> authenticate(String username, String password) {
        if (username == null || username.trim().isEmpty() || password == null || password.isEmpty()) {
            return Optional.empty();
        }

        Optional<String> hashOpt = userRepository.getPasswordHash(username);
        if (hashOpt.isEmpty()) {
            return Optional.empty();
        }

        if (passwordEncoder.matches(password, hashOpt.get())) {
            return userRepository.findByUsername(username);
        }

        return Optional.empty();
    }
}
