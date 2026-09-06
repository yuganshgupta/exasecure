package com.examsystem.client.admin;

import com.examsystem.client.api.ApiClient;
import com.examsystem.client.ui.AppIcon;
import com.examsystem.client.admin.views.AdminDashboardView;
import com.examsystem.client.admin.views.AdminLoginView;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;

public class AdminApp extends Application {
    private Stage primaryStage;
    private ApiClient apiClient;
    private StackPane rootLayout;

    @Override
    public void start(Stage stage) {
        this.primaryStage = stage;
        this.apiClient = new ApiClient();
        this.rootLayout = new StackPane();

        Scene scene = new Scene(rootLayout, 1180, 760);
        com.examsystem.client.theme.ThemeManager.getInstance().applyTheme(scene);
        stage.setTitle("ExaSecure — Administrative Console");
        AppIcon.applyTo(stage);
        stage.setScene(scene);
        stage.setMinWidth(960);
        stage.setMinHeight(640);

        showLogin();
        stage.show();
    }

    public void showLogin() {
        AdminLoginView loginView = new AdminLoginView(this, apiClient);
        rootLayout.getChildren().setAll(loginView.getView());
    }

    public void showDashboard() {
        AdminDashboardView dashboardView = new AdminDashboardView(this, apiClient);
        rootLayout.getChildren().setAll(dashboardView.getView());
    }

    public Stage getPrimaryStage() {
        return primaryStage;
    }

    public static void main(String[] args) {
        launch(args);
    }
}
