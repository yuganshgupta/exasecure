package com.examsystem.models;

public class User {
    private int id;
    private String username;
    private String password; 
    private String fullName;
    private String enrollmentNumber; // New field
    private String section;          // New field
    private String role; 

    public User() {}

    public User(int id, String username, String password, String fullName, String enrollmentNumber, String section, String role) {
        this.id = id;
        this.username = username;
        this.password = password;
        this.fullName = fullName;
        this.enrollmentNumber = enrollmentNumber;
        this.section = section;
        this.role = role;
    }

    public int getId() { return id; }
    public String getUsername() { return username; }
    public String getPassword() { return password; }
    public String getFullName() { return fullName; }
    public String getEnrollmentNumber() { return enrollmentNumber; }
    public String getSection() { return section; }
    public String getRole() { return role; }

    public void setId(int id) { this.id = id; }
    public void setUsername(String username) { this.username = username; }
    public void setPassword(String password) { this.password = password; }
    public void setFullName(String fullName) { this.fullName = fullName; }
    public void setEnrollmentNumber(String enrollmentNumber) { this.enrollmentNumber = enrollmentNumber; }
    public void setSection(String section) { this.section = section; }
    public void setRole(String role) { this.role = role; }
}