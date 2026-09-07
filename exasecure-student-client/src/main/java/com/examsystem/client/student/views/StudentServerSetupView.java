package com.examsystem.client.student.views;

import com.examsystem.client.student.StudentApp;
import com.examsystem.client.student.StudentConnection;
import com.examsystem.client.theme.ThemeManager;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import java.io.IOException;

/** Small logged-out setup screen. Cancel leaves existing state intact, or exits on first run. */
public final class StudentServerSetupView {
    private final StackPane view;

    public StudentServerSetupView(StudentApp app, StudentConnection connection) {
        Label title = new Label("Configure Server");
        title.getStyleClass().add("section-title");
        Label help = new Label("Enter the Server address supplied by your institution.");
        help.setWrapText(true);
        TextField address = new TextField(connection.client() == null ? "" : connection.client().getConfig().baseUrl());
        address.setPromptText("http://exam-server:8089");
        address.setAccessibleText("Server address");
        Label feedback = new Label(connection.error());
        feedback.setWrapText(true);
        feedback.getStyleClass().add("status-error");
        Button save = new Button("Save Server");
        save.getStyleClass().add("btn-primary");
        save.setDisable(!connection.canConfigure());
        address.setDisable(!connection.canConfigure());
        save.setOnAction(e -> {
            try {
                app.saveServer(address.getText());
            } catch (IllegalArgumentException | IllegalStateException ex) {
                feedback.setText(ex.getMessage());
            } catch (IOException ex) {
                feedback.setText("Unable to save Server settings. Check your user-folder permissions and local filesystem.");
            }
        });
        address.setOnAction(e -> save.fire());
        Button cancel = new Button(connection.client() == null ? "Exit" : "Cancel");
        cancel.setOnAction(e -> {
            if (connection.client() == null) app.getPrimaryStage().close();
            else app.showLogin();
        });
        VBox card = new VBox(16, title, help, address, feedback, new HBox(12, save, cancel), ThemeManager.getInstance().createThemeSelector());
        card.setMaxWidth(480);
        card.setAlignment(Pos.CENTER_LEFT);
        card.getStyleClass().addAll("theme-card", "login-card");
        view = new StackPane(card);
        view.getStyleClass().addAll("app-root", "login-page");
    }

    public Parent getView() { return view; }
}
