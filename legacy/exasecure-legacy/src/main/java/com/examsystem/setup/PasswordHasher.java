package com.examsystem.setup;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import org.mindrot.jbcrypt.BCrypt;

/** Setup-only helper that hashes one password received over standard input. */
public final class PasswordHasher {
    private PasswordHasher() {
    }

    public static void main(String[] args) throws Exception {
        BufferedReader reader = new BufferedReader(
                new InputStreamReader(System.in, StandardCharsets.UTF_8));
        String password = reader.readLine();

        if (password == null || password.isEmpty()) {
            System.err.println("Password input is required.");
            System.exit(2);
        }

        int byteLength = password.getBytes(StandardCharsets.UTF_8).length;
        if (byteLength > 72) {
            System.err.println("BCrypt input must be at most 72 UTF-8 bytes.");
            System.exit(2);
        }

        System.out.println(BCrypt.hashpw(password, BCrypt.gensalt()));
    }
}
