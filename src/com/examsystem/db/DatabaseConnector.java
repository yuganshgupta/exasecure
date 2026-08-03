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
                throw new RuntimeException(
                    "FATAL: db.properties not found. Cannot start without database configuration. " +
                    "Create a db.properties file with db.url, db.user, and db.password.", e);
            }
            Class.forName("com.mysql.cj.jdbc.Driver"); 
        } catch (ClassNotFoundException e) {
            throw new RuntimeException("MySQL JDBC Driver not found. Add mysql-connector-j to classpath.", e);
        }
    }

    public static Connection getConnection() throws SQLException {
        return DriverManager.getConnection(
                props.getProperty("db.url"),
                props.getProperty("db.user"),
                props.getProperty("db.password")
        );
    }
}