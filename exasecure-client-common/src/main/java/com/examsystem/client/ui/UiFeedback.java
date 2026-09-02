package com.examsystem.client.ui;

import com.examsystem.client.async.AsyncRunner;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;

import java.util.Optional;

public class UiFeedback {

    public static void showError(String title, String message) {
        AsyncRunner.runOnFxThread(() -> {
            try {
                Alert alert = new Alert(Alert.AlertType.ERROR);
                alert.setTitle(title);
                alert.setHeaderText(null);
                alert.setContentText(message);
                alert.showAndWait();
            } catch (Exception e) {
                System.err.println("[ERROR] " + title + ": " + message);
            }
        });
    }

    public static void showInfo(String title, String message) {
        AsyncRunner.runOnFxThread(() -> {
            try {
                Alert alert = new Alert(Alert.AlertType.INFORMATION);
                alert.setTitle(title);
                alert.setHeaderText(null);
                alert.setContentText(message);
                alert.showAndWait();
            } catch (Exception e) {
                System.out.println("[INFO] " + title + ": " + message);
            }
        });
    }

    public static boolean showConfirmation(String title, String header, String content) {
        try {
            Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
            alert.setTitle(title);
            alert.setHeaderText(header);
            alert.setContentText(content);
            Optional<ButtonType> result = alert.showAndWait();
            return result.isPresent() && result.get() == ButtonType.OK;
        } catch (Exception e) {
            return true;
        }
    }
}
