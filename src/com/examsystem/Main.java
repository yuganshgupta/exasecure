package com.examsystem;

import com.examsystem.gui.LoginWindow;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.*;

/**
 * Entry point: launches the Swing GUI (LoginWindow) on the EDT.
 */
public class Main {
    
    // Create a logger for uncaught exceptions
    private static final Logger LOGGER = Logger.getLogger(Main.class.getName());

    public static void main(String[] args) {
        // Run database migrations first
        com.examsystem.db.DatabaseMigrator.migrate();

        // Show unhandled exceptions to help troubleshooting
        Thread.setDefaultUncaughtExceptionHandler((t, e) -> {
            LOGGER.log(Level.SEVERE, "Unhandled Exception caught in thread " + t.getName(), e);
            String msg = e.getMessage() != null ? e.getMessage() : e.toString();
            if (e instanceof RuntimeException && e.getCause() instanceof java.sql.SQLException) {
                msg = "Database Error:\n" + e.getMessage();
            }
            JOptionPane.showMessageDialog(null, msg, "Application Error", JOptionPane.ERROR_MESSAGE);
        });

        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (ClassNotFoundException | InstantiationException | IllegalAccessException | UnsupportedLookAndFeelException ignored) {
            // Ignore LookAndFeel exceptions and fallback to the default Java look
        }

        SwingUtilities.invokeLater(() -> new LoginWindow().setVisible(true));
    }
}