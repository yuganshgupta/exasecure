package com.examsystem;

import com.examsystem.gui.LoginWindow;

import javax.swing.*;

/**
 * Entry point: launches the Swing GUI (LoginWindow) on the EDT.
 */
public class Main {
    public static void main(String[] args) {
        // Show unhandled exceptions to help troubleshooting
        Thread.setDefaultUncaughtExceptionHandler((t, e) -> {
            e.printStackTrace();
            JOptionPane.showMessageDialog(null, e.toString(), "Unhandled Error", JOptionPane.ERROR_MESSAGE);
        });

        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {}

        SwingUtilities.invokeLater(() -> new LoginWindow().setVisible(true));
    }
}