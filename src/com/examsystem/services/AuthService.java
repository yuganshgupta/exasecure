package com.examsystem.services;

import com.examsystem.dao.UserDAO;
import com.examsystem.models.User;


//  Simple authentication service
public class AuthService {
    private final UserDAO userDAO = new UserDAO();

    public User login(String username, String password) {
        if (username == null || username.isEmpty()) return null;
        if (password == null || password.isEmpty()) return null;
        
        User user = userDAO.findByUsername(username);
        if (user == null) return null;
        
        try {
            if (org.mindrot.jbcrypt.BCrypt.checkpw(password, user.getPassword())) {
                return user;
            }
        } catch (IllegalArgumentException e) {
            // Happens if the database contains plaintext passwords before migration
            System.err.println("Invalid salt version - likely legacy plaintext password: " + e.getMessage());
            // Optional: fallback to plaintext for seamless transition if desired, but strict mode is better.
        }
        return null;
    }
}