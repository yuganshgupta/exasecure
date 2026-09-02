package com.examsystem.db;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Simple test program that uses DatabaseConnector.getConnection()
 * to verify JDBC connectivity and print some metadata.
 *
 * Run after you set the DB credentials in DatabaseConnector.java
 */
public class TestDBConnection {
    
    // Create a logger for this class
    private static final Logger LOGGER = Logger.getLogger(TestDBConnection.class.getName());

    public static void main(String[] args) {
        System.out.println("Testing DB connection via DatabaseConnector...");
        try (Connection conn = DatabaseConnector.getConnection()) {
            if (conn != null && !conn.isClosed()) {
                DatabaseMetaData md = conn.getMetaData();
                System.out.println("Connected to: " + md.getURL());
                System.out.println("DB Product : " + md.getDatabaseProductName() + " " + md.getDatabaseProductVersion());
                System.out.println("Driver     : " + md.getDriverName() + " " + md.getDriverVersion());
                System.out.println("User       : " + md.getUserName());
                System.out.println("SUCCESS: JDBC connection OK.");
            } else {
                System.err.println("ERROR: Connection was null or closed.");
            }
        } catch (SQLException e) {
            // Use the logger instead of e.printStackTrace()
            LOGGER.log(Level.SEVERE, "ERROR: JDBC connection failed", e);
            System.exit(2);
        }
    }
}