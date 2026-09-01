package com.examsystem;

import com.examsystem.gui.LoginWindow;
import java.awt.GraphicsEnvironment;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.*;

/**
 * Entry point: launches the Swing GUI (LoginWindow) on the EDT.
 */
public class Main {
    private static final Logger LOGGER = Logger.getLogger(Main.class.getName());

    public static void main(String[] args) {
        Thread.setDefaultUncaughtExceptionHandler((t, e) -> {
            LOGGER.log(Level.SEVERE, "Unhandled Exception caught in thread " + t.getName(), e);
            String msg = e.getMessage() != null ? e.getMessage() : e.toString();
            if (e instanceof RuntimeException && e.getCause() instanceof java.sql.SQLException) {
                msg = "Database Error:\n" + e.getMessage();
            }
            if (!GraphicsEnvironment.isHeadless()) {
                JOptionPane.showMessageDialog(null, msg, "Application Error", JOptionPane.ERROR_MESSAGE);
            }
        });

        if (hasArgument(args, "--version")) {
            System.out.println("ExaSecure " + getVersion());
            return;
        }

        try {
            com.examsystem.db.DatabaseMigrator.migrate();
        } catch (RuntimeException | ExceptionInInitializerError e) {
            LOGGER.log(Level.SEVERE, "Application startup failed", e);
            String message = e.getMessage() != null ? e.getMessage() : e.toString();
            if (!GraphicsEnvironment.isHeadless()) {
                JOptionPane.showMessageDialog(null, message, "ExaSecure Startup Error", JOptionPane.ERROR_MESSAGE);
            }
            System.exit(1);
            return;
        }

        if (hasArgument(args, "--check")) {
            System.out.println("ExaSecure " + getVersion() + " is ready.");
            return;
        }

        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (ClassNotFoundException | InstantiationException | IllegalAccessException | UnsupportedLookAndFeelException ignored) {
            // Ignore LookAndFeel exceptions and fallback to the default Java look
        }

        SwingUtilities.invokeLater(() -> new LoginWindow().setVisible(true));
    }

    private static boolean hasArgument(String[] args, String expected) {
        if (args == null) return false;
        for (String arg : args) {
            if (expected.equals(arg)) return true;
        }
        return false;
    }

    private static String getVersion() {
        String version = Main.class.getPackage().getImplementationVersion();
        return version != null ? version : "development";
    }
}
