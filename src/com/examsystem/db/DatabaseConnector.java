package com.examsystem.db;

import java.io.FileInputStream;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;

/**
 * JDBC connection provider.
 * Reads credentials from db.properties in the project root.
 */
public class DatabaseConnector {

    private static final Properties props = new Properties();

    static {
        try {
            // Try loading properties from 'db.properties'
            try (FileInputStream fis = new FileInputStream("db.properties")) {
                props.load(fis);
            } catch (IOException e) {
                // Fallback defaults if file is missing (useful for testing/demos)
                System.err.println("Warning: db.properties not found. Using default settings.");
                props.setProperty("db.url", "jdbc:mysql://localhost:3306/secure_exam_db?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true");
                props.setProperty("db.user", "root");
                props.setProperty("db.password", "baba1234"); 
            }
            Class.forName("com.mysql.cj.jdbc.Driver"); 
        } catch (ClassNotFoundException e) {
            throw new RuntimeException("MySQL JDBC Driver not found. Add mysql-connector-j to classpath.", e);
        }
    }

    public static Connection getConnection() throws SQLException {
        try {
            return DriverManager.getConnection(
                    props.getProperty("db.url"),
                    props.getProperty("db.user"),
                    props.getProperty("db.password")
            );
        } catch (SQLException e) {
            throw new RuntimeException("Failed to connect to the database. Please check your network or database server.", e);
        }
    }
}