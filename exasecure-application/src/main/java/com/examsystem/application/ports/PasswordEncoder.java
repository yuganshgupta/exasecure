package com.examsystem.application.ports;

public interface PasswordEncoder {
    boolean matches(String rawPassword, String encodedPassword);
}
