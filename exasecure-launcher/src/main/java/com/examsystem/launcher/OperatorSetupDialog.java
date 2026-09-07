package com.examsystem.launcher;

import com.examsystem.client.theme.ThemeManager;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.stage.Window;
import java.io.IOException;

final class OperatorSetupDialog {
    static void show(Window owner, OperatorSettings store) {
        Dialog<Void> dialog = new Dialog<>();
        dialog.initOwner(owner);
        dialog.setTitle("Installed Server configuration");
        TextField url = new TextField();
        url.setPromptText("jdbc:mysql://db-host:3306/database");
        TextField user = new TextField();
        PasswordField password = new PasswordField();
        TextField port = new TextField("8089");
        Label feedback = new Label();
        feedback.setWrapText(true);
        feedback.getStyleClass().add("status-error");
        try {
            var existing = store.load();
            if (existing != null) {
                url.setText(existing.url);
                user.setText(existing.user);
                password.setText(existing.password);
                port.setText(Integer.toString(existing.port));
            }
        } catch (IOException | IllegalArgumentException e) {
            feedback.setText("Existing installed settings could not be read. Enter replacement settings to save.");
        }
        Label help = new Label("Use an externally provisioned MySQL database and restricted account. Settings apply to installed execution. Current development launches use the existing development configuration.");
        help.setWrapText(true);
        Label location = new Label("Saved to: " + store.file());
        location.setWrapText(true);
        VBox fields = new VBox(10, help, new Label("Database URL"), url, new Label("Database user"), user,
                new Label("Database password"), password, new Label("Server port"), port, location, feedback);
        fields.setPrefWidth(500);
        dialog.getDialogPane().setContent(fields);
        ButtonType save = new ButtonType("Save", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(save, ButtonType.CANCEL);
        dialog.getDialogPane().lookupButton(save).addEventFilter(javafx.event.ActionEvent.ACTION, e -> {
            try {
                store.save(new OperatorSettings.DatabaseSettings(url.getText(), user.getText(), password.getText(), port.getText()));
            } catch (IllegalArgumentException ex) {
                feedback.setText(ex.getMessage());
                e.consume();
            } catch (IOException | UnsupportedOperationException ex) {
                feedback.setText("Settings were not saved. A local filesystem with verified owner-only permissions and atomic replacement is required.");
                e.consume();
            }
        });
        ThemeManager.getInstance().applyTheme(dialog);
        dialog.showAndWait();
        password.clear();
    }
}
