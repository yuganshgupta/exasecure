package com.examsystem.client.ui;

import com.examsystem.client.async.AsyncRunner;
import com.examsystem.client.theme.ThemeManager;
import javafx.application.Platform;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.stage.Window;

import java.util.Optional;

public class UiFeedback {

    public static void showError(String title, String message) {
        showError(null, title, message);
    }

    public static void showError(Window owner, String title, String message) {
        AsyncRunner.runOnFxThread(() -> showModalError(owner, title, message));
    }

    public static void showWarning(String title, String message) {
        showWarning(null, title, message);
    }

    public static void showWarning(Window owner, String title, String message) {
        AsyncRunner.runOnFxThread(() -> showModalWarning(owner, title, message));
    }

    public static void showInfo(String title, String message) {
        showInfo(null, title, message);
    }

    public static void showInfo(Window owner, String title, String message) {
        AsyncRunner.runOnFxThread(() -> showModalInfo(owner, title, message));
    }

    public static void showModalError(Window owner, String title, String message) {
        showModalAlert(owner, Alert.AlertType.ERROR, title, null, message);
    }

    public static void showModalWarning(Window owner, String title, String message) {
        showModalAlert(owner, Alert.AlertType.WARNING, title, null, message);
    }

    public static void showModalInfo(Window owner, String title, String message) {
        showModalAlert(owner, Alert.AlertType.INFORMATION, title, null, message);
    }

    public static void showModalAlert(Window owner, Alert.AlertType type, String title, String header, String content) {
        try {
            Alert alert = new Alert(type);
            if (owner != null) {
                alert.initOwner(owner);
            }
            alert.setTitle(title);
            alert.setHeaderText(header);
            alert.setContentText(content);
            ThemeManager.getInstance().applyTheme(alert);
            alert.showAndWait();
        } catch (Exception e) {
            System.err.println("[" + type + "] " + title + ": " + content);
        }
    }

    public static boolean showConfirmation(String title, String header, String content) {
        return showConfirmation(null, title, header, content);
    }

    public static boolean showConfirmation(Window owner, String title, String header, String content) {
        try {
            Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
            if (owner != null) {
                alert.initOwner(owner);
            }
            alert.setTitle(title);
            alert.setHeaderText(header);
            alert.setContentText(content);
            ThemeManager.getInstance().applyTheme(alert);
            Optional<ButtonType> result = alert.showAndWait();
            return result.isPresent() && result.get() == ButtonType.OK;
        } catch (Exception e) {
            return false;
        }
    }
}
