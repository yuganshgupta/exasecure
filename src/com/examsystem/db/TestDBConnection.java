package com.examsystem.db;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;

/**
 * Simple test program that uses DatabaseConnector.getConnection()
 * to verify JDBC connectivity and print some metadata.
 *
 * Run after you set the DB credentials in DatabaseConnector.java
 */
public class TestDBConnection {
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
            System.err.println("ERROR: JDBC connection failed:");
            e.printStackTrace();
            System.exit(2);
        }
    }
}