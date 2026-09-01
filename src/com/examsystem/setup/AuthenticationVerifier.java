package com.examsystem.setup;

import com.examsystem.models.User;
import com.examsystem.services.AuthService;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/** Setup-only smoke test for the same authentication path used by the GUI. */
public final class AuthenticationVerifier {
    private AuthenticationVerifier() {
    }

    public static void main(String[] args) throws Exception {
        BufferedReader reader = new BufferedReader(
                new InputStreamReader(System.in, StandardCharsets.UTF_8));
        String username = reader.readLine();
        String password = reader.readLine();

        if (username == null || username.isEmpty() || password == null || password.isEmpty()) {
            System.err.println("Username and password input are required.");
            System.exit(2);
        }

        User user = new AuthService().login(username, password);
        if (user == null) {
            System.err.println("Authentication smoke test failed.");
            System.exit(3);
        }

        System.out.println("AUTHENTICATION_OK id=" + user.getId() + " role=" + user.getRole());
    }
}
