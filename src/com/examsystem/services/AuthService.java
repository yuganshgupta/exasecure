package com.examsystem.services;

import com.examsystem.dao.UserDAO;
import com.examsystem.models.User;


//  Simple authentication service
public class AuthService {
    private final UserDAO userDAO = new UserDAO();

    public User login(String username, String password) {
        if (username == null || username.isEmpty()) return null;
        if (password == null || password.isEmpty()) return null;
        return userDAO.findByUsernameAndPassword(username, password);
    }
}