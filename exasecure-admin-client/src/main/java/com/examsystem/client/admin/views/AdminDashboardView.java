package com.examsystem.client.admin.views;

import com.examsystem.client.admin.AdminApp;
import com.examsystem.client.api.ApiClient;
import com.examsystem.client.api.ApiException;
import com.examsystem.client.async.AsyncRunner;
import com.examsystem.client.ui.UiFeedback;
import com.examsystem.contracts.AdminAttemptDetailDto;
import com.examsystem.contracts.AdminExamStatisticsDto;
import com.examsystem.contracts.admin.*;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class AdminDashboardView {
    private final AdminApp app;
    private final ApiClient apiClient;
    private final BorderPane view;
    private final TabPane tabPane;

    // Command / Summary Metric Labels
    private final Label metricUsersLabel = new Label("Users: -");
    private final Label metricExamsLabel = new Label("Exams: -");
    private final Label metricAttemptsLabel = new Label("Attempts: -");

    // 1. Users Tab Controls
    private final List<AdminUserDto> allUsersList = new ArrayList<>();
    private final TextField userSearchField = new TextField();
    private final TableView<AdminUserDto> usersTable = new TableView<>();
    private final Label usersStatusLabel = new Label();

    // 2. Exams Tab Controls
    private final List<AdminExamDto> allExamsList = new ArrayList<>();
    private final TableView<AdminExamDto> examsTable = new TableView<>();
    private final Label examsStatusLabel = new Label();

    // 3. Question Bank Tab Controls
    private final ComboBox<AdminExamDto> questionBankExamCombo = new ComboBox<>();
    private final TableView<AdminQuestionDefinitionDto> qbQuestionsTable = new TableView<>();
    private final VBox qbOptionsDetailsBox = new VBox(10);
    private final Label qbStatusLabel = new Label();

    // 4. Attempts Tab Controls
    private final ComboBox<AdminExamDto> attemptsExamCombo = new ComboBox<>();
    private final TextField attemptsExamIdField = new TextField();
    private final TableView<AdminAttemptDto> attemptsTable = new TableView<>();
    private final Label attemptsStatusLabel = new Label();

    // 5. Proctoring Tab Controls
    private final Stage primaryStage;
    private final ComboBox<AdminExamDto> proctorExamCombo = new ComboBox<>();
    private final TableView<AdminAttemptDto> proctorAttemptsTable = new TableView<>();
    private final TableView<AdminProctorEventDto> proctorEventsTable = new TableView<>();
    private final ImageView evidenceImageView = new ImageView();
    private final ScrollPane evidenceScrollPane = new ScrollPane();
    private final Label evidenceStatusLabel = new Label("Select an exam, then an attempt, and a proctor event to review.");
    private final Label evidenceZoomLabel = new Label("100%");
    private double evidenceZoom = 1.0;
    private final Map<Integer, byte[]> evidenceCache = new HashMap<>();
    private byte[] currentEvidenceBytes = null;
    private int currentEvidenceEventId = -1;
    private long currentQbReqId = 0;
    private long currentAttemptsReqId = 0;
    private long currentProctorAttemptsReqId = 0;
    private long currentProctorEventsReqId = 0;
    private long currentEvidenceReqId = 0;

    public AdminDashboardView(AdminApp app, ApiClient apiClient) {
        this.app = app;
        this.apiClient = apiClient;
        this.primaryStage = app.getPrimaryStage();
        this.view = new BorderPane();
        this.tabPane = new TabPane();

        buildUI();
        loadUsers();
        loadExams();
    }

    public Parent getView() {
        return view;
    }

    private void buildUI() {
        view.setStyle("-fx-background-color: #f8fafc;");

        // Top Navigation Bar
        VBox topContainer = new VBox();

        HBox topBar = new HBox(16);
        topBar.setPadding(new Insets(14, 24, 14, 24));
        topBar.setAlignment(Pos.CENTER_LEFT);
        topBar.setStyle("-fx-background-color: #0f172a;");

        Label brand = new Label("ExaSecure");
        brand.setFont(Font.font("Segoe UI", FontWeight.BOLD, 20));
        brand.setStyle("-fx-text-fill: white;");

        Label adminBadge = new Label("ADMIN CONSOLE");
        adminBadge.setStyle("-fx-background-color: #dc2626; -fx-text-fill: white; -fx-font-weight: bold; -fx-font-size: 11px; -fx-padding: 3 8; -fx-background-radius: 4px;");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        String adminName = apiClient.getSessionState().fullName() != null
                ? apiClient.getSessionState().fullName()
                : apiClient.getSessionState().username();
        Label userLabel = new Label("Admin: " + adminName);
        userLabel.setStyle("-fx-text-fill: #cbd5e1; -fx-font-size: 13px;");

        Button logoutBtn = new Button("Sign Out");
        logoutBtn.setStyle("-fx-background-color: #334155; -fx-text-fill: white; -fx-background-radius: 6px; -fx-cursor: hand;");
        logoutBtn.setOnAction(e -> {
            apiClient.logout();
            app.showLogin();
        });

        var themeSelector = com.examsystem.client.theme.ThemeManager.getInstance().createThemeSelector();
        topBar.getChildren().addAll(brand, adminBadge, spacer, themeSelector, userLabel, logoutBtn);

        // Command & Overview Strip
        HBox overviewStrip = new HBox(20);
        overviewStrip.setPadding(new Insets(8, 24, 8, 24));
        overviewStrip.setAlignment(Pos.CENTER_LEFT);
        overviewStrip.setStyle("-fx-background-color: #1e293b; -fx-border-color: #334155; -fx-border-width: 1 0 0 0;");

        metricUsersLabel.setStyle("-fx-text-fill: #94a3b8; -fx-font-size: 12px;");
        metricExamsLabel.setStyle("-fx-text-fill: #94a3b8; -fx-font-size: 12px;");
        metricAttemptsLabel.setStyle("-fx-text-fill: #94a3b8; -fx-font-size: 12px;");

        Region stripSpacer = new Region();
        HBox.setHgrow(stripSpacer, Priority.ALWAYS);

        Label quickLabel = new Label("Quick Actions:");
        quickLabel.setStyle("-fx-text-fill: #64748b; -fx-font-size: 11px;");

        Button quickAddExamBtn = new Button("+ New Exam");
        quickAddExamBtn.setStyle("-fx-background-color: #2563eb; -fx-text-fill: white; -fx-font-size: 11px; -fx-padding: 3 8; -fx-background-radius: 4px; -fx-cursor: hand;");
        quickAddExamBtn.setOnAction(e -> showCreateExamDialog());

        Button quickAddUserBtn = new Button("+ New User");
        quickAddUserBtn.setStyle("-fx-background-color: #3b82f6; -fx-text-fill: white; -fx-font-size: 11px; -fx-padding: 3 8; -fx-background-radius: 4px; -fx-cursor: hand;");
        quickAddUserBtn.setOnAction(e -> showCreateUserDialog());

        overviewStrip.getChildren().addAll(metricUsersLabel, metricExamsLabel, metricAttemptsLabel, stripSpacer, quickLabel, quickAddExamBtn, quickAddUserBtn);

        topContainer.getChildren().addAll(topBar, overviewStrip);
        view.setTop(topContainer);

        // Tab Navigation
        tabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        tabPane.getTabs().addAll(
                createUsersTab(),
                createExamsTab(),
                createQuestionBankTab(),
                createAttemptsTab(),
                createProctoringTab()
        );

        view.setCenter(tabPane);
    }

    // ==========================================
    // 1. Users Management Tab
    // ==========================================

    private Tab createUsersTab() {
        Tab tab = new Tab("User Accounts");
        VBox content = new VBox(14);
        content.setPadding(new Insets(20));

        HBox toolbar = new HBox(12);
        toolbar.setAlignment(Pos.CENTER_LEFT);

        Label title = new Label("System Users");
        title.setFont(Font.font("Segoe UI", FontWeight.BOLD, 18));
        title.setStyle("-fx-text-fill: #0f172a;");

        userSearchField.setPromptText("🔍 Search by name, username, enrollment...");
        userSearchField.setPrefWidth(260);
        userSearchField.textProperty().addListener((obs, oldVal, newVal) -> filterUsers(newVal));

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button addBtn = new Button("+ Add User");
        addBtn.getStyleClass().add("btn-primary");
        addBtn.setOnAction(e -> showCreateUserDialog());

        Button editBtn = new Button("Edit User");
        editBtn.getStyleClass().add("btn-secondary");
        editBtn.setOnAction(e -> {
            AdminUserDto selected = usersTable.getSelectionModel().getSelectedItem();
            if (selected == null) {
                UiFeedback.showError(primaryStage, "Selection Required", "Please select a user to edit.");
                return;
            }
            showEditUserDialog(selected);
        });

        Button deactivateBtn = new Button("Deactivate");
        deactivateBtn.getStyleClass().add("btn-danger");
        deactivateBtn.setOnAction(e -> {
            AdminUserDto selected = usersTable.getSelectionModel().getSelectedItem();
            if (selected == null) {
                UiFeedback.showError(primaryStage, "Selection Required", "Please select a user to deactivate.");
                return;
            }
            confirmDeactivateUser(selected);
        });

        Button reactivateBtn = new Button("Reactivate");
        reactivateBtn.getStyleClass().add("btn-success");
        reactivateBtn.setOnAction(e -> {
            AdminUserDto selected = usersTable.getSelectionModel().getSelectedItem();
            if (selected == null) {
                UiFeedback.showError(primaryStage, "Selection Required", "Please select a user to reactivate.");
                return;
            }
            confirmReactivateUser(selected);
        });

        Button deleteBtn = new Button("Delete User");
        deleteBtn.getStyleClass().add("btn-danger");
        deleteBtn.setOnAction(e -> {
            AdminUserDto selected = usersTable.getSelectionModel().getSelectedItem();
            if (selected == null) {
                UiFeedback.showError(primaryStage, "Selection Required", "Please select a user to delete.");
                return;
            }
            confirmDeleteUser(selected);
        });

        Button refreshBtn = new Button("Refresh");
        refreshBtn.getStyleClass().add("btn-secondary");
        refreshBtn.setOnAction(e -> loadUsers());

        // Dynamic enabling based on selection state
        editBtn.setDisable(true);
        deactivateBtn.setDisable(true);
        reactivateBtn.setDisable(true);
        deleteBtn.setDisable(true);

        usersTable.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, selected) -> {
            boolean hasSel = (selected != null);
            editBtn.setDisable(!hasSel);
            deleteBtn.setDisable(!hasSel);
            if (hasSel) {
                deactivateBtn.setDisable(!selected.isActive());
                reactivateBtn.setDisable(selected.isActive());
            } else {
                deactivateBtn.setDisable(true);
                reactivateBtn.setDisable(true);
            }
        });

        toolbar.getChildren().addAll(title, userSearchField, spacer, addBtn, editBtn, deactivateBtn, reactivateBtn, deleteBtn, refreshBtn);

        setupUsersTable();

        content.getChildren().addAll(toolbar, usersStatusLabel, usersTable);
        VBox.setVgrow(usersTable, Priority.ALWAYS);
        tab.setContent(content);
        return tab;
    }

    private void setupUsersTable() {
        usersTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        usersTable.setPlaceholder(new Label("No user accounts found."));

        TableColumn<AdminUserDto, String> idCol = new TableColumn<>("ID");
        idCol.setCellValueFactory(d -> new SimpleStringProperty(String.valueOf(d.getValue().id())));
        idCol.setMaxWidth(60);

        TableColumn<AdminUserDto, String> usernameCol = new TableColumn<>("Username");
        usernameCol.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().username()));
        usernameCol.setPrefWidth(120);

        TableColumn<AdminUserDto, String> nameCol = new TableColumn<>("Full Name");
        nameCol.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().fullName()));
        nameCol.setPrefWidth(160);

        TableColumn<AdminUserDto, String> enrollCol = new TableColumn<>("Enrollment #");
        enrollCol.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().enrollmentNumber() != null ? d.getValue().enrollmentNumber() : "-"));
        enrollCol.setPrefWidth(120);

        TableColumn<AdminUserDto, String> secCol = new TableColumn<>("Section");
        secCol.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().section() != null ? d.getValue().section() : "-"));
        secCol.setMaxWidth(80);

        TableColumn<AdminUserDto, String> roleCol = new TableColumn<>("Role");
        roleCol.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().role()));
        roleCol.setMaxWidth(90);

        TableColumn<AdminUserDto, String> statusCol = new TableColumn<>("Status");
        statusCol.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().isActive() ? "Active" : "Inactive"));
        statusCol.setMaxWidth(80);

        usersTable.getColumns().addAll(idCol, usernameCol, nameCol, enrollCol, secCol, roleCol, statusCol);
    }

    private void loadUsers() {
        usersTable.getItems().clear();
        allUsersList.clear();
        usersStatusLabel.setText("Loading user accounts from server...");
        AsyncRunner.runAsync(
                apiClient::adminListUsers,
                (List<AdminUserDto> users) -> {
                    usersStatusLabel.setText("");
                    if (users != null) {
                        allUsersList.addAll(users);
                        filterUsers(userSearchField.getText());
                        metricUsersLabel.setText("Users: " + users.size());
                    }
                },
                (Throwable err) -> usersStatusLabel.setText("Failed to load users: " + err.getMessage())
        );
    }

    private void filterUsers(String query) {
        if (query == null || query.isBlank()) {
            usersTable.getItems().setAll(allUsersList);
            return;
        }
        String lower = query.toLowerCase().trim();
        List<AdminUserDto> filtered = allUsersList.stream()
                .filter(u -> (u.username() != null && u.username().toLowerCase().contains(lower))
                        || (u.fullName() != null && u.fullName().toLowerCase().contains(lower))
                        || (u.enrollmentNumber() != null && u.enrollmentNumber().toLowerCase().contains(lower))
                        || (u.section() != null && u.section().toLowerCase().contains(lower)))
                .toList();
        usersTable.getItems().setAll(filtered);
    }

    private void showCreateUserDialog() {
        Dialog<AdminCreateUserRequestDto> dialog = new Dialog<>();
        dialog.initOwner(primaryStage);
        com.examsystem.client.theme.ThemeManager.getInstance().applyTheme(dialog);
        dialog.setTitle("Create New User Account");
        dialog.setHeaderText("Enter details for new system user:");

        ButtonType createBtnType = new ButtonType("Create User", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(createBtnType, ButtonType.CANCEL);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(12);
        grid.setPadding(new Insets(20, 20, 10, 10));

        TextField username = new TextField();
        PasswordField password = new PasswordField();
        TextField fullName = new TextField();
        TextField enrollment = new TextField();
        TextField section = new TextField();
        ComboBox<String> role = new ComboBox<>();
        role.getItems().addAll("STUDENT", "ADMIN");
        role.setValue("STUDENT");

        grid.add(new Label("Username:"), 0, 0);
        grid.add(username, 1, 0);
        grid.add(new Label("Password:"), 0, 1);
        grid.add(password, 1, 1);
        grid.add(new Label("Full Name:"), 0, 2);
        grid.add(fullName, 1, 2);
        grid.add(new Label("Enrollment #:"), 0, 3);
        grid.add(enrollment, 1, 3);
        grid.add(new Label("Section:"), 0, 4);
        grid.add(section, 1, 4);
        grid.add(new Label("Role:"), 0, 5);
        grid.add(role, 1, 5);

        dialog.getDialogPane().setContent(grid);

        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == createBtnType) {
                return new AdminCreateUserRequestDto(
                        username.getText().trim(),
                        password.getText(),
                        fullName.getText().trim(),
                        enrollment.getText().trim().isEmpty() ? null : enrollment.getText().trim(),
                        section.getText().trim().isEmpty() ? null : section.getText().trim(),
                        role.getValue()
                );
            }
            return null;
        });

        Optional<AdminCreateUserRequestDto> result = dialog.showAndWait();
        result.ifPresent(req -> {
            if (req.username().isEmpty() || req.password().isEmpty() || req.fullName().isEmpty()) {
                UiFeedback.showError(primaryStage, "Validation Error", "Username, password, and full name are required.");
                return;
            }
            AsyncRunner.runAsync(
                    () -> apiClient.adminCreateUser(req),
                    (Integer id) -> {
                        UiFeedback.showInfo(primaryStage, "User Created", "User created successfully with ID: " + id);
                        loadUsers();
                    },
                    (Throwable err) -> UiFeedback.showError(primaryStage, "Creation Failed", err.getMessage())
            );
        });
    }

    private void showEditUserDialog(AdminUserDto user) {
        Dialog<AdminUpdateUserRequestDto> dialog = new Dialog<>();
        dialog.initOwner(primaryStage);
        com.examsystem.client.theme.ThemeManager.getInstance().applyTheme(dialog);
        dialog.setTitle("Edit User Account");
        dialog.setHeaderText("Update details for user " + user.username() + " (ID: " + user.id() + "):");

        ButtonType saveBtnType = new ButtonType("Save Changes", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(saveBtnType, ButtonType.CANCEL);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(12);
        grid.setPadding(new Insets(20, 20, 10, 10));

        TextField username = new TextField(user.username());
        PasswordField password = new PasswordField();
        password.setPromptText("Leave blank to keep existing password");
        TextField fullName = new TextField(user.fullName());
        TextField enrollment = new TextField(user.enrollmentNumber() != null ? user.enrollmentNumber() : "");
        TextField section = new TextField(user.section() != null ? user.section() : "");
        ComboBox<String> role = new ComboBox<>();
        role.getItems().addAll("STUDENT", "ADMIN");
        role.setValue(user.role());

        grid.add(new Label("Username:"), 0, 0);
        grid.add(username, 1, 0);
        grid.add(new Label("New Password:"), 0, 1);
        grid.add(password, 1, 1);
        grid.add(new Label("Full Name:"), 0, 2);
        grid.add(fullName, 1, 2);
        grid.add(new Label("Enrollment #:"), 0, 3);
        grid.add(enrollment, 1, 3);
        grid.add(new Label("Section:"), 0, 4);
        grid.add(section, 1, 4);
        grid.add(new Label("Role:"), 0, 5);
        grid.add(role, 1, 5);

        dialog.getDialogPane().setContent(grid);

        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == saveBtnType) {
                String p = password.getText().isBlank() ? null : password.getText();
                return new AdminUpdateUserRequestDto(
                        username.getText().trim(),
                        p,
                        fullName.getText().trim(),
                        enrollment.getText().trim().isEmpty() ? null : enrollment.getText().trim(),
                        section.getText().trim().isEmpty() ? null : section.getText().trim(),
                        role.getValue()
                );
            }
            return null;
        });

        Optional<AdminUpdateUserRequestDto> result = dialog.showAndWait();
        result.ifPresent(req -> {
            AsyncRunner.runAsync(
                    () -> {
                        apiClient.adminUpdateUser(user.id(), req);
                        return true;
                    },
                    (Boolean ok) -> {
                        UiFeedback.showInfo("User Updated", "User " + user.username() + " was updated successfully.");
                        loadUsers();
                    },
                    (Throwable err) -> UiFeedback.showError("Update Failed", err.getMessage())
            );
        });
    }

    private void confirmDeactivateUser(AdminUserDto user) {
        boolean confirmed = UiFeedback.showConfirmation(
                "Deactivate User",
                "Deactivate user account: " + user.username() + "?",
                "This user will no longer be able to log in to the system. All existing sessions will be terminated."
        );
        if (confirmed) {
            AsyncRunner.runAsync(
                    () -> {
                        apiClient.adminDeactivateUser(user.id());
                        return true;
                    },
                    (Boolean ok) -> {
                        UiFeedback.showInfo("User Deactivated", "User " + user.username() + " has been deactivated.");
                        loadUsers();
                    },
                    (Throwable err) -> UiFeedback.showError("Deactivation Failed", err.getMessage())
            );
        }
    }

    private void confirmReactivateUser(AdminUserDto user) {
        if (user.isActive()) {
            UiFeedback.showInfo("User Active", "User @" + user.username() + " is already active.");
            return;
        }
        boolean confirmed = UiFeedback.showConfirmation(
                "Confirm Reactivation",
                "Reactivate user @" + user.username() + "?",
                "This will restore login access for " + user.fullName() + " (" + user.role() + ")."
        );
        if (!confirmed) return;

        usersStatusLabel.setText("Reactivating user @" + user.username() + "...");
        AsyncRunner.runAsync(
                () -> {
                    apiClient.adminReactivateUser(user.id());
                    return null;
                },
                ignored -> {
                    UiFeedback.showInfo("User Reactivated", "User @" + user.username() + " has been successfully reactivated.");
                    loadUsers();
                },
                err -> {
                    usersStatusLabel.setText("Failed to reactivate user: " + err.getMessage());
                    UiFeedback.showError(primaryStage, "Reactivation Failed", err.getMessage());
                }
        );
    }

    private void confirmDeleteUser(AdminUserDto user) {
        boolean confirmed = UiFeedback.showConfirmation(
                primaryStage,
                "Confirm Permanent Deletion",
                "Permanently delete user '" + user.username() + "' (ID #" + user.id() + ")?",
                "WARNING: This will permanently remove the user account from the system.\n\n"
                        + "Note: Deletion will only succeed if this user has no exam attempts, created exams, or audit records. "
                        + "If historical records exist, please use Deactivate instead."
        );
        if (!confirmed) return;

        usersStatusLabel.setText("Deleting user #" + user.id() + "...");
        AsyncRunner.runAsync(
                () -> {
                    apiClient.adminDeleteUser(user.id());
                    return true;
                },
                ignored -> {
                    UiFeedback.showInfo(primaryStage, "User Deleted", "User '" + user.username() + "' has been permanently deleted.");
                    loadUsers();
                },
                err -> {
                    usersStatusLabel.setText("Delete failed: " + err.getMessage());
                    UiFeedback.showError(primaryStage, "Deletion Rejected", err.getMessage());
                }
        );
    }

    // ==========================================
    // 2. Exams Management Tab
    // ==========================================

    private Tab createExamsTab() {
        Tab tab = new Tab("Examinations");
        VBox content = new VBox(14);
        content.setPadding(new Insets(20));

        HBox toolbar = new HBox(12);
        toolbar.setAlignment(Pos.CENTER_LEFT);

        Label title = new Label("Exam Definitions");
        title.setFont(Font.font("Segoe UI", FontWeight.BOLD, 18));

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button createBtn = new Button("+ Create New Exam");
        createBtn.getStyleClass().add("btn-primary");
        createBtn.setOnAction(e -> showCreateExamDialog());

        Button reviseBtn = new Button("Revise Selected Exam");
        reviseBtn.getStyleClass().add("btn-secondary");
        reviseBtn.setOnAction(e -> {
            AdminExamDto selected = examsTable.getSelectionModel().getSelectedItem();
            if (selected == null) {
                UiFeedback.showError(primaryStage, "Selection Required", "Please select an exam to revise.");
                return;
            }
            showReviseExamDialog(selected);
        });

        Button deactivateBtn = new Button("Deactivate");
        deactivateBtn.getStyleClass().add("btn-danger");
        deactivateBtn.setOnAction(e -> {
            AdminExamDto selected = examsTable.getSelectionModel().getSelectedItem();
            if (selected == null) {
                UiFeedback.showError(primaryStage, "Selection Required", "Please select an exam to deactivate.");
                return;
            }
            confirmDeactivateExam(selected);
        });

        Button reactivateBtn = new Button("Reactivate");
        reactivateBtn.getStyleClass().add("btn-success");
        reactivateBtn.setOnAction(e -> {
            AdminExamDto selected = examsTable.getSelectionModel().getSelectedItem();
            if (selected == null) {
                UiFeedback.showError(primaryStage, "Selection Required", "Please select an exam to reactivate.");
                return;
            }
            confirmReactivateExam(selected);
        });

        Button refreshBtn = new Button("Refresh");
        refreshBtn.getStyleClass().add("btn-secondary");
        refreshBtn.setOnAction(e -> loadExams());

        // Dynamic enabling based on selection state
        reviseBtn.setDisable(true);
        deactivateBtn.setDisable(true);
        reactivateBtn.setDisable(true);

        examsTable.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, selected) -> {
            boolean hasSel = (selected != null);
            reviseBtn.setDisable(!hasSel);
            if (hasSel) {
                deactivateBtn.setDisable(!selected.isActive());
                reactivateBtn.setDisable(selected.isActive());
            } else {
                deactivateBtn.setDisable(true);
                reactivateBtn.setDisable(true);
            }
        });

        toolbar.getChildren().addAll(title, spacer, createBtn, reviseBtn, deactivateBtn, reactivateBtn, refreshBtn);

        setupExamsTable();

        content.getChildren().addAll(toolbar, examsStatusLabel, examsTable);
        VBox.setVgrow(examsTable, Priority.ALWAYS);
        tab.setContent(content);
        return tab;
    }

    private void setupExamsTable() {
        examsTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        examsTable.setPlaceholder(new Label("No examinations defined in the system."));

        TableColumn<AdminExamDto, String> idCol = new TableColumn<>("ID");
        idCol.setCellValueFactory(d -> new SimpleStringProperty(String.valueOf(d.getValue().id())));
        idCol.setMaxWidth(70);

        TableColumn<AdminExamDto, String> titleCol = new TableColumn<>("Title");
        titleCol.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().title()));

        TableColumn<AdminExamDto, String> durCol = new TableColumn<>("Duration (mins)");
        durCol.setCellValueFactory(d -> new SimpleStringProperty(String.valueOf(d.getValue().durationMinutes())));

        TableColumn<AdminExamDto, String> verCol = new TableColumn<>("Version");
        verCol.setCellValueFactory(d -> new SimpleStringProperty("v" + d.getValue().versionNumber()));

        TableColumn<AdminExamDto, String> activeCol = new TableColumn<>("Status");
        activeCol.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().isActive() ? "Active" : "Archived / Inactive"));
        activeCol.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setStyle("");
                } else {
                    setText(item);
                    if ("Active".equals(item)) {
                        setStyle("-fx-text-fill: #16a34a; -fx-font-weight: bold;");
                    } else {
                        setStyle("-fx-text-fill: #64748b;");
                    }
                }
            }
        });

        TableColumn<AdminExamDto, String> supersedesCol = new TableColumn<>("Supersedes ID");
        supersedesCol.setCellValueFactory(d -> new SimpleStringProperty(
                d.getValue().supersedesExamId() != null ? "#" + d.getValue().supersedesExamId() : "-"));

        examsTable.getColumns().addAll(idCol, titleCol, durCol, verCol, activeCol, supersedesCol);
    }

    private void loadExams() {
        loadExams(-1);
    }

    private void loadExams(int reselectExamId) {
        examsTable.getItems().clear();
        allExamsList.clear();
        examsStatusLabel.setText("Loading examinations from server...");
        AsyncRunner.runAsync(
                apiClient::adminListExams,
                (List<AdminExamDto> exams) -> {
                    examsStatusLabel.setText("");
                    if (exams != null) {
                        allExamsList.addAll(exams);
                        examsTable.getItems().addAll(exams);
                        updateExamCombos();
                        long activeCount = exams.stream().filter(AdminExamDto::isActive).count();
                        metricExamsLabel.setText("Exams: " + exams.size() + " (" + activeCount + " active)");

                        if (reselectExamId > 0) {
                            for (AdminExamDto exam : exams) {
                                if (exam.id() == reselectExamId) {
                                    examsTable.getSelectionModel().select(exam);
                                    examsTable.scrollTo(exam);
                                    break;
                                }
                            }
                        }
                    }
                },
                (Throwable err) -> examsStatusLabel.setText("Failed to load exams: " + err.getMessage())
        );
    }

    private void updateExamCombos() {
        AdminExamDto curQb = questionBankExamCombo.getValue();
        questionBankExamCombo.getItems().setAll(allExamsList);
        if (curQb != null) {
            allExamsList.stream().filter(e -> e.id() == curQb.id()).findFirst()
                    .ifPresentOrElse(questionBankExamCombo::setValue, () -> {
                        if (!allExamsList.isEmpty()) questionBankExamCombo.setValue(allExamsList.get(0));
                    });
        } else if (!allExamsList.isEmpty()) {
            questionBankExamCombo.setValue(allExamsList.get(0));
        }

        AdminExamDto curAttempts = attemptsExamCombo.getValue();
        attemptsExamCombo.getItems().setAll(allExamsList);
        if (curAttempts != null) {
            allExamsList.stream().filter(e -> e.id() == curAttempts.id()).findFirst()
                    .ifPresentOrElse(attemptsExamCombo::setValue, () -> {
                        if (!allExamsList.isEmpty()) attemptsExamCombo.setValue(allExamsList.get(0));
                    });
        } else if (!allExamsList.isEmpty()) {
            attemptsExamCombo.setValue(allExamsList.get(0));
        }

        AdminExamDto curProctor = proctorExamCombo.getValue();
        proctorExamCombo.getItems().setAll(allExamsList);
        if (curProctor != null) {
            allExamsList.stream().filter(e -> e.id() == curProctor.id()).findFirst()
                    .ifPresentOrElse(proctorExamCombo::setValue, () -> {
                        if (!allExamsList.isEmpty()) proctorExamCombo.setValue(allExamsList.get(0));
                    });
        } else if (!allExamsList.isEmpty()) {
            proctorExamCombo.setValue(allExamsList.get(0));
        }
    }

    private void showCreateExamDialog() {
        ExamDefinitionBuilderDialog builder = new ExamDefinitionBuilderDialog(
                "Create New Examination",
                "Define exam metadata and questions:",
                null,
                null
        );
        builder.initOwner(primaryStage);
        com.examsystem.client.theme.ThemeManager.getInstance().applyTheme(builder);
        Optional<AdminExamDefinitionRequestDto> result = builder.showAndWait();
        result.ifPresent(req -> {
            AsyncRunner.runAsync(
                    () -> apiClient.adminCreateExam(req),
                    (AdminExamMutationResultDto res) -> {
                        UiFeedback.showInfo(primaryStage, "Exam Created", "Exam created with ID #" + res.id() + " (Version " + res.versionNumber() + ").");
                        loadExams(res.id());
                    },
                    (Throwable err) -> UiFeedback.showError(primaryStage, "Creation Failed", err.getMessage())
            );
        });
    }

    private void showReviseExamDialog(AdminExamDto exam) {
        examsStatusLabel.setText("Loading questions for exam #" + exam.id() + "...");
        AsyncRunner.runAsync(
                () -> apiClient.adminGetExamQuestions(exam.id()),
                (List<AdminQuestionDefinitionDto> questions) -> {
                    examsStatusLabel.setText("");
                    ExamDefinitionBuilderDialog builder = new ExamDefinitionBuilderDialog(
                            "Revise Examination (v" + (exam.versionNumber() + 1) + ")",
                            "Creating a copy-on-write revision superseding exam #" + exam.id() + ":",
                            exam,
                            questions
                    );
                    builder.initOwner(primaryStage);
                    com.examsystem.client.theme.ThemeManager.getInstance().applyTheme(builder);
                    Optional<AdminExamDefinitionRequestDto> result = builder.showAndWait();
                    result.ifPresent(req -> {
                        AsyncRunner.runAsync(
                                () -> apiClient.adminReviseExam(exam.id(), req),
                                (AdminExamMutationResultDto res) -> {
                                    UiFeedback.showInfo(primaryStage, "Exam Revised", "New revision created with ID #" + res.id() + " (Version " + res.versionNumber() + ").");
                                    loadExams(res.id());
                                },
                                (Throwable err) -> UiFeedback.showError(primaryStage, "Revision Failed", err.getMessage())
                        );
                    });
                },
                (Throwable err) -> {
                    examsStatusLabel.setText("");
                    UiFeedback.showError(primaryStage, "Failed to Load Questions", err.getMessage());
                }
        );
    }

    private void confirmDeactivateExam(AdminExamDto exam) {
        int targetId = exam.id();
        boolean confirmed = UiFeedback.showConfirmation(
                primaryStage,
                "Deactivate Exam",
                "Deactivate exam #" + targetId + " (" + exam.title() + ")?",
                "Students will no longer be able to start new attempts for this exam."
        );
        if (confirmed) {
            AsyncRunner.runAsync(
                    () -> {
                        apiClient.adminDeactivateExam(targetId);
                        return true;
                    },
                    (Boolean ok) -> {
                        UiFeedback.showInfo(primaryStage, "Exam Deactivated", "Exam #" + targetId + " has been deactivated.");
                        loadExams(targetId);
                    },
                    (Throwable err) -> UiFeedback.showError(primaryStage, "Deactivation Failed", err.getMessage())
            );
        }
    }

    private void confirmReactivateExam(AdminExamDto exam) {
        if (exam.isActive()) {
            UiFeedback.showInfo(primaryStage, "Exam Active", "Exam '" + exam.title() + "' is already active.");
            return;
        }
        int targetId = exam.id();
        boolean confirmed = UiFeedback.showConfirmation(
                primaryStage,
                "Confirm Reactivation",
                "Reactivate exam #" + targetId + " (" + exam.title() + ")?",
                "This will re-enable this exam definition for students without creating a new revision."
        );
        if (!confirmed) return;

        examsStatusLabel.setText("Reactivating exam #" + targetId + "...");
        AsyncRunner.runAsync(
                () -> {
                    apiClient.adminReactivateExam(targetId);
                    return null;
                },
                ignored -> {
                    UiFeedback.showInfo(primaryStage, "Exam Reactivated", "Exam #" + targetId + " has been successfully reactivated.");
                    loadExams(targetId);
                },
                err -> {
                    examsStatusLabel.setText("Failed to reactivate exam: " + err.getMessage());
                    UiFeedback.showError(primaryStage, "Reactivation Failed", err.getMessage());
                }
        );
    }

    // ==========================================
    // 3. Question Bank Tab (Dedicated Feature)
    // ==========================================

    private Tab createQuestionBankTab() {
        Tab tab = new Tab("Question Bank");
        VBox content = new VBox(14);
        content.setPadding(new Insets(20));

        HBox topBar = new HBox(12);
        topBar.setAlignment(Pos.CENTER_LEFT);

        Label examSelectLabel = new Label("Select Examination:");
        examSelectLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: #1e293b;");

        questionBankExamCombo.setPrefWidth(280);
        questionBankExamCombo.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(AdminExamDto item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? "" : "#" + item.id() + " - " + item.title() + " (v" + item.versionNumber() + ")");
            }
        });
        questionBankExamCombo.setButtonCell(new ListCell<>() {
            @Override
            protected void updateItem(AdminExamDto item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? "Select Exam..." : "#" + item.id() + " - " + item.title() + " (v" + item.versionNumber() + ")");
            }
        });
        questionBankExamCombo.valueProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) loadQuestionBank(newVal.id());
        });

        Button loadQbBtn = new Button("Load Questions");
        loadQbBtn.setStyle("-fx-background-color: #2563eb; -fx-text-fill: white; -fx-font-weight: bold; -fx-background-radius: 6px; -fx-padding: 6 14; -fx-cursor: hand;");
        loadQbBtn.setOnAction(e -> {
            AdminExamDto sel = questionBankExamCombo.getValue();
            if (sel != null) loadQuestionBank(sel.id());
        });

        topBar.getChildren().addAll(examSelectLabel, questionBankExamCombo, loadQbBtn);

        // Split master-detail view
        SplitPane split = new SplitPane();

        // Left: Questions Table
        VBox left = new VBox(8);
        setupQuestionBankTable();
        left.getChildren().addAll(new Label("Questions List:"), qbQuestionsTable);
        VBox.setVgrow(qbQuestionsTable, Priority.ALWAYS);

        // Right: Question & Options Preview Card
        VBox right = new VBox(12);
        right.setPadding(new Insets(14));
        right.getStyleClass().add("theme-card");

        Label detailsTitle = new Label("Question & Answer Key Preview");
        detailsTitle.setFont(Font.font("Segoe UI", FontWeight.BOLD, 15));
        detailsTitle.setStyle("-fx-text-fill: #0f172a;");

        ScrollPane optScroll = new ScrollPane(qbOptionsDetailsBox);
        optScroll.setFitToWidth(true);
        optScroll.setStyle("-fx-background: transparent; -fx-background-color: transparent; -fx-border-color: transparent;");

        right.getChildren().addAll(detailsTitle, optScroll);
        VBox.setVgrow(optScroll, Priority.ALWAYS);

        split.getItems().addAll(left, right);
        split.setDividerPositions(0.48);

        content.getChildren().addAll(topBar, qbStatusLabel, split);
        VBox.setVgrow(split, Priority.ALWAYS);
        tab.setContent(content);
        return tab;
    }

    private void setupQuestionBankTable() {
        qbQuestionsTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        qbQuestionsTable.setPlaceholder(new Label("Select an examination above to view its question bank."));

        TableColumn<AdminQuestionDefinitionDto, String> numCol = new TableColumn<>("#");
        numCol.setCellValueFactory(d -> new SimpleStringProperty(String.valueOf(qbQuestionsTable.getItems().indexOf(d.getValue()) + 1)));
        numCol.setMaxWidth(45);

        TableColumn<AdminQuestionDefinitionDto, String> textCol = new TableColumn<>("Question Text");
        textCol.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().text()));
        textCol.setPrefWidth(220);

        TableColumn<AdminQuestionDefinitionDto, String> typeCol = new TableColumn<>("Type");
        typeCol.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().type()));
        typeCol.setMaxWidth(75);

        TableColumn<AdminQuestionDefinitionDto, String> optCountCol = new TableColumn<>("Options");
        optCountCol.setCellValueFactory(d -> new SimpleStringProperty(String.valueOf(d.getValue().options().size())));
        optCountCol.setMaxWidth(65);

        qbQuestionsTable.getColumns().addAll(numCol, textCol, typeCol, optCountCol);

        qbQuestionsTable.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                renderQuestionBankDetails(newVal);
            }
        });
    }

    private void loadQuestionBank(int examId) {
        long reqId = ++currentQbReqId;
        qbQuestionsTable.getItems().clear();
        qbOptionsDetailsBox.getChildren().clear();
        qbStatusLabel.setText("Loading question bank for exam #" + examId + "...");

        AsyncRunner.runAsync(
                () -> apiClient.adminGetExamQuestions(examId),
                (List<AdminQuestionDefinitionDto> questions) -> {
                    if (reqId != currentQbReqId) return;
                    AdminExamDto curExam = questionBankExamCombo.getValue();
                    if (curExam != null && curExam.id() != examId) return;

                    qbStatusLabel.setText("");
                    if (questions != null) {
                        qbQuestionsTable.getItems().setAll(questions);
                        if (!questions.isEmpty()) {
                            qbQuestionsTable.getSelectionModel().select(0);
                        }
                    }
                },
                (Throwable err) -> {
                    if (reqId != currentQbReqId) return;
                    qbStatusLabel.setText("Failed to load questions: " + err.getMessage());
                }
        );
    }

    private void renderQuestionBankDetails(AdminQuestionDefinitionDto q) {
        qbOptionsDetailsBox.getChildren().clear();

        Label qLabel = new Label(q.text());
        qLabel.setFont(Font.font("Segoe UI", FontWeight.BOLD, 14));
        qLabel.setWrapText(true);
        qLabel.setStyle("-fx-text-fill: #1e293b;");

        Label typeBadge = new Label("Type: " + q.type());
        typeBadge.setStyle("-fx-background-color: #f1f5f9; -fx-text-fill: #475569; -fx-font-size: 11px; -fx-padding: 2 6; -fx-background-radius: 4px;");

        qbOptionsDetailsBox.getChildren().addAll(qLabel, typeBadge, new Separator());

        Label optionsHeader = new Label("Options & Answer Keys:");
        optionsHeader.setFont(Font.font("Segoe UI", FontWeight.BOLD, 12));
        optionsHeader.setStyle("-fx-text-fill: #475569;");
        qbOptionsDetailsBox.getChildren().add(optionsHeader);

        int optNum = 1;
        for (AdminOptionDefinitionDto opt : q.options()) {
            HBox optRow = new HBox(10);
            optRow.setAlignment(Pos.CENTER_LEFT);
            optRow.setPadding(new Insets(8, 12, 8, 12));
            optRow.setStyle(opt.correct()
                    ? "-fx-background-color: #f0fdf4; -fx-border-color: #86efac; -fx-border-radius: 6px; -fx-background-radius: 6px;"
                    : "-fx-background-color: #f8fafc; -fx-border-color: #e2e8f0; -fx-border-radius: 6px; -fx-background-radius: 6px;");

            Label optNumLabel = new Label(optNum + ".");
            optNumLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: #334155;");

            Label optTextLabel = new Label(opt.text());
            optTextLabel.setStyle("-fx-text-fill: #1e293b;");
            optTextLabel.setWrapText(true);

            Region rowSpacer = new Region();
            HBox.setHgrow(rowSpacer, Priority.ALWAYS);

            Label badge = new Label(opt.correct() ? "✔ CORRECT" : "✘ Incorrect");
            badge.setStyle(opt.correct()
                    ? "-fx-background-color: #16a34a; -fx-text-fill: white; -fx-font-size: 10px; -fx-font-weight: bold; -fx-padding: 2 6; -fx-background-radius: 4px;"
                    : "-fx-background-color: #e2e8f0; -fx-text-fill: #64748b; -fx-font-size: 10px; -fx-padding: 2 6; -fx-background-radius: 4px;");

            optRow.getChildren().addAll(optNumLabel, optTextLabel, rowSpacer, badge);
            qbOptionsDetailsBox.getChildren().add(optRow);
            optNum++;
        }
    }

    // ==========================================
    // 4. Attempts & Results Tab
    // ==========================================

    private Tab createAttemptsTab() {
        Tab tab = new Tab("Attempts & Results");
        VBox content = new VBox(14);
        content.setPadding(new Insets(20));

        HBox filterBar = new HBox(12);
        filterBar.setAlignment(Pos.CENTER_LEFT);

        Label label = new Label("Select Exam:");
        label.setStyle("-fx-font-weight: bold;");

        attemptsExamCombo.setPrefWidth(240);
        attemptsExamCombo.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(AdminExamDto item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? "" : "#" + item.id() + " - " + item.title());
            }
        });
        attemptsExamCombo.setButtonCell(new ListCell<>() {
            @Override
            protected void updateItem(AdminExamDto item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? "Select Exam..." : "#" + item.id() + " - " + item.title());
            }
        });
        attemptsExamCombo.valueProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                attemptsExamIdField.setText(String.valueOf(newVal.id()));
                loadAttempts();
            }
        });

        attemptsExamIdField.setPromptText("Or ID (e.g. 1)");
        attemptsExamIdField.setPrefWidth(90);

        Button loadBtn = new Button("Load Attempts");
        loadBtn.getStyleClass().add("btn-primary");
        loadBtn.setOnAction(e -> loadAttempts());

        Region filterSpacer = new Region();
        HBox.setHgrow(filterSpacer, Priority.ALWAYS);

        Button exportCsvBtn = new Button("📥 Export to CSV");
        exportCsvBtn.getStyleClass().add("btn-secondary");
        exportCsvBtn.setOnAction(e -> exportAttemptsToCsv());

        Button viewDetailsBtn = new Button("📋 View Report Card");
        viewDetailsBtn.getStyleClass().add("btn-secondary");
        viewDetailsBtn.setOnAction(e -> {
            AdminAttemptDto sel = attemptsTable.getSelectionModel().getSelectedItem();
            if (sel == null) {
                UiFeedback.showError(primaryStage, "Selection Required", "Please select an attempt to view report card.");
                return;
            }
            openAttemptReportCard(sel);
        });

        Button statsBtn = new Button("📊 Exam Analytics");
        statsBtn.getStyleClass().add("btn-secondary");
        statsBtn.setOnAction(e -> openExamStatistics());

        Button inspectProctorBtn = new Button("🛡️ Inspect Proctor Events");
        inspectProctorBtn.getStyleClass().add("btn-secondary");
        inspectProctorBtn.setOnAction(e -> {
            AdminAttemptDto sel = attemptsTable.getSelectionModel().getSelectedItem();
            if (sel == null) {
                UiFeedback.showError(primaryStage, "Selection Required", "Please select an attempt to inspect proctoring events.");
                return;
            }
            AdminExamDto exam = attemptsExamCombo.getValue();
            if (exam != null) {
                proctorExamCombo.setValue(exam);
            }
            tabPane.getSelectionModel().select(4); // Switch to Proctoring tab
            proctorAttemptsTable.getSelectionModel().select(sel);
            loadProctorEventsForAttempt(sel);
        });

        Button deleteAttemptBtn = new Button("🗑️ Delete Attempt");
        deleteAttemptBtn.getStyleClass().add("btn-danger");
        deleteAttemptBtn.setOnAction(e -> {
            AdminAttemptDto sel = attemptsTable.getSelectionModel().getSelectedItem();
            if (sel == null) {
                UiFeedback.showError(primaryStage, "Selection Required", "Please select an attempt to delete.");
                return;
            }
            confirmDeleteAttempt(sel);
        });

        // Dynamic enabling based on selection
        viewDetailsBtn.setDisable(true);
        inspectProctorBtn.setDisable(true);
        deleteAttemptBtn.setDisable(true);
        attemptsTable.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, sel) -> {
            boolean hasSel = (sel != null);
            viewDetailsBtn.setDisable(!hasSel);
            inspectProctorBtn.setDisable(!hasSel);
            deleteAttemptBtn.setDisable(!hasSel);
        });

        filterBar.getChildren().addAll(label, attemptsExamCombo, attemptsExamIdField, loadBtn, filterSpacer, exportCsvBtn, viewDetailsBtn, statsBtn, inspectProctorBtn, deleteAttemptBtn);

        setupAttemptsTable();

        content.getChildren().addAll(filterBar, attemptsStatusLabel, attemptsTable);
        VBox.setVgrow(attemptsTable, Priority.ALWAYS);
        tab.setContent(content);
        return tab;
    }

    private void setupAttemptsTable() {
        attemptsTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        attemptsTable.setPlaceholder(new Label("Select or enter an Exam ID above to view student attempts."));

        TableColumn<AdminAttemptDto, String> idCol = new TableColumn<>("Attempt ID");
        idCol.setCellValueFactory(d -> new SimpleStringProperty("#" + d.getValue().id()));
        idCol.setMaxWidth(90);

        TableColumn<AdminAttemptDto, String> stuIdCol = new TableColumn<>("Student ID");
        stuIdCol.setCellValueFactory(d -> new SimpleStringProperty(String.valueOf(d.getValue().studentId())));
        stuIdCol.setMaxWidth(90);

        TableColumn<AdminAttemptDto, String> nameCol = new TableColumn<>("Student Name");
        nameCol.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().studentName()));

        TableColumn<AdminAttemptDto, String> enrollCol = new TableColumn<>("Enrollment");
        enrollCol.setCellValueFactory(d -> new SimpleStringProperty(
                d.getValue().enrollmentNumber() != null ? d.getValue().enrollmentNumber() : "-"));
        enrollCol.setMaxWidth(110);

        TableColumn<AdminAttemptDto, String> startCol = new TableColumn<>("Start Time");
        startCol.setCellValueFactory(d -> new SimpleStringProperty(
                d.getValue().startTime() != null ? d.getValue().startTime() : "-"));

        TableColumn<AdminAttemptDto, String> statusCol = new TableColumn<>("Status");
        statusCol.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().status()));

        TableColumn<AdminAttemptDto, String> scoreCol = new TableColumn<>("Score");
        scoreCol.setCellValueFactory(d -> new SimpleStringProperty(
                d.getValue().score() != null ? d.getValue().score() + " pts" : "Pending"));

        TableColumn<AdminAttemptDto, String> violationsCol = new TableColumn<>("Violations");
        violationsCol.setCellValueFactory(d -> new SimpleStringProperty(String.valueOf(d.getValue().focusLostCount())));
        violationsCol.setMaxWidth(90);

        TableColumn<AdminAttemptDto, String> proctoringCol = new TableColumn<>("Proctoring");
        proctoringCol.setCellValueFactory(d -> new SimpleStringProperty(
                d.getValue().flagged() ? "⚠️ FLAGGED" : "CLEAN"));
        proctoringCol.setPrefWidth(120);
        proctoringCol.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setStyle("");
                } else {
                    setText(item);
                    if (item.contains("FLAGGED")) {
                        setStyle("-fx-text-fill: #dc2626; -fx-font-weight: bold;");
                    } else {
                        setStyle("-fx-text-fill: #16a34a; -fx-font-weight: bold;");
                    }
                }
            }
        });

        attemptsTable.getColumns().addAll(idCol, stuIdCol, nameCol, enrollCol, startCol, statusCol, scoreCol, violationsCol, proctoringCol);

        attemptsTable.setRowFactory(tv -> {
            TableRow<AdminAttemptDto> row = new TableRow<>();
            row.setOnMouseClicked(event -> {
                if (event.getClickCount() == 2 && (!row.isEmpty())) {
                    openAttemptReportCard(row.getItem());
                }
            });
            return row;
        });
    }

    public static String escapeCsvCell(String value) {
        if (value == null) return "\"\"";
        if (!value.isEmpty()) {
            char first = value.charAt(0);
            if (first == '=' || first == '+' || first == '-' || first == '@' || first == '\t' || first == '\r') {
                value = "'" + value;
            }
        }
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }

    private void loadAttempts() {
        String input = attemptsExamIdField.getText().trim();
        if (input.isEmpty()) {
            attemptsStatusLabel.setText("Please enter an Exam ID.");
            return;
        }
        int examId;
        try {
            examId = Integer.parseInt(input);
        } catch (NumberFormatException e) {
            attemptsStatusLabel.setText("Exam ID must be an integer.");
            return;
        }

        long reqId = ++currentAttemptsReqId;
        attemptsTable.getItems().clear();
        attemptsStatusLabel.setText("Loading attempts for exam #" + examId + "...");
        AsyncRunner.runAsync(
                () -> apiClient.adminGetAttemptHistory(examId),
                (List<AdminAttemptDto> attempts) -> {
                    if (reqId != currentAttemptsReqId) return;
                    String curInput = attemptsExamIdField.getText().trim();
                    if (!curInput.equals(String.valueOf(examId))) return;

                    attemptsStatusLabel.setText("");
                    if (attempts != null) {
                        attemptsTable.getItems().addAll(attempts);
                        metricAttemptsLabel.setText("Attempts: " + attempts.size() + " for exam #" + examId);
                    }
                },
                (Throwable err) -> {
                    if (reqId != currentAttemptsReqId) return;
                    attemptsStatusLabel.setText("Failed to load attempts: " + err.getMessage());
                }
        );
    }

    private void confirmDeleteAttempt(AdminAttemptDto attempt) {
        boolean confirmed = UiFeedback.showConfirmation(
                primaryStage,
                "Confirm Delete Attempt",
                "Permanently delete attempt #" + attempt.id() + " for " + attempt.studentName() + "?",
                "Student: " + attempt.studentName() + " (Enrollment: " + (attempt.enrollmentNumber() != null ? attempt.enrollmentNumber() : "N/A") + ")\n"
                        + "Score: " + (attempt.score() != null ? attempt.score() + " pts" : "Pending") + "\n"
                        + "Violations: " + attempt.focusLostCount() + " (" + (attempt.flagged() ? "FLAGGED" : "CLEAN") + ")\n\n"
                        + "WARNING: This will permanently remove this student attempt, all associated answers, and proctoring evidence. "
                        + "The exam definition and question bank will remain completely untouched."
        );
        if (!confirmed) return;

        attemptsStatusLabel.setText("Deleting attempt #" + attempt.id() + "...");
        AsyncRunner.runAsync(
                () -> {
                    apiClient.adminDeleteAttempt(attempt.id());
                    return true;
                },
                ignored -> {
                    UiFeedback.showInfo(primaryStage, "Attempt Deleted", "Attempt #" + attempt.id() + " was successfully deleted.");
                    loadAttempts();
                },
                err -> {
                    attemptsStatusLabel.setText("Delete failed: " + err.getMessage());
                    UiFeedback.showError(primaryStage, "Deletion Failed", err.getMessage());
                }
        );
    }

    private void openAttemptReportCard(AdminAttemptDto attempt) {
        attemptsStatusLabel.setText("Loading report card for attempt #" + attempt.id() + "...");
        AsyncRunner.runAsync(
                () -> apiClient.adminGetAttemptDetails(attempt.id()),
                (List<AdminAttemptDetailDto> details) -> {
                    attemptsStatusLabel.setText("");
                    AttemptDetailsDialog dialog = new AttemptDetailsDialog(attempt, details);
                    dialog.initOwner(primaryStage);
                    com.examsystem.client.theme.ThemeManager.getInstance().applyTheme(dialog);
                    dialog.showAndWait();
                },
                (Throwable err) -> {
                    attemptsStatusLabel.setText("");
                    UiFeedback.showError(primaryStage, "Failed to Load Details", err.getMessage());
                }
        );
    }

    private void openExamStatistics() {
        String input = attemptsExamIdField.getText().trim();
        if (input.isEmpty()) {
            UiFeedback.showError(primaryStage, "Selection Required", "Please select an exam first to view statistics.");
            return;
        }
        int examId;
        try {
            examId = Integer.parseInt(input);
        } catch (NumberFormatException e) {
            UiFeedback.showError(primaryStage, "Invalid Input", "Exam ID must be an integer.");
            return;
        }

        attemptsStatusLabel.setText("Loading analytics for exam #" + examId + "...");
        AsyncRunner.runAsync(
                () -> apiClient.adminGetExamStatistics(examId),
                (AdminExamStatisticsDto stats) -> {
                    attemptsStatusLabel.setText("");
                    ExamStatisticsDialog dialog = new ExamStatisticsDialog(stats);
                    dialog.initOwner(primaryStage);
                    com.examsystem.client.theme.ThemeManager.getInstance().applyTheme(dialog);
                    dialog.showAndWait();
                },
                (Throwable err) -> {
                    attemptsStatusLabel.setText("");
                    UiFeedback.showError(primaryStage, "Failed to Load Statistics", err.getMessage());
                }
        );
    }

    private void exportAttemptsToCsv() {
        List<AdminAttemptDto> items = new ArrayList<>(attemptsTable.getItems());
        if (items.isEmpty()) {
            UiFeedback.showError(primaryStage, "Nothing to Export", "There are no attempts loaded in the table to export.");
            return;
        }

        String examId = attemptsExamIdField.getText().trim();
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Export Attempts to CSV");
        fileChooser.setInitialFileName("attempts-exam-" + (examId.isEmpty() ? "all" : examId) + ".csv");
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV Files (*.csv)", "*.csv"));

        File file = fileChooser.showSaveDialog(view.getScene() != null ? view.getScene().getWindow() : null);
        if (file == null) return;

        attemptsStatusLabel.setText("Exporting attempts to CSV...");
        AsyncRunner.runAsync(
                () -> {
                    try (FileWriter writer = new FileWriter(file)) {
                        writer.write("Attempt ID,Student ID,Student Name,Enrollment,Start Time,Status,Score,Violations,Flagged,Proctoring Status\n");
                        for (AdminAttemptDto a : items) {
                            String studentName = escapeCsvCell(a.studentName());
                            String enrollment = escapeCsvCell(a.enrollmentNumber() != null ? a.enrollmentNumber() : "");
                            String startTime = escapeCsvCell(a.startTime() != null ? a.startTime().toString() : "");
                            String status = escapeCsvCell(a.status());
                            String score = a.score() != null ? String.valueOf(a.score()) : "";
                            String violations = String.valueOf(a.focusLostCount());
                            String flagged = String.valueOf(a.flagged());
                            String proctoringStatus = escapeCsvCell(a.proctoringStatus() != null ? a.proctoringStatus() : "CLEAN");
                            writer.write(String.format("%d,%d,%s,%s,%s,%s,%s,%s,%s,%s\n",
                                    a.id(),
                                    a.studentId(),
                                    studentName,
                                    enrollment,
                                    startTime,
                                    status,
                                    score,
                                    violations,
                                    flagged,
                                    proctoringStatus));
                        }
                    }
                    return items.size();
                },
                (Integer count) -> {
                    attemptsStatusLabel.setText("");
                    UiFeedback.showInfo(primaryStage, "Export Complete", "Successfully exported " + count + " attempts to:\n" + file.getAbsolutePath());
                },
                (Throwable err) -> {
                    attemptsStatusLabel.setText("");
                    UiFeedback.showError(primaryStage, "Export Failed", "Error saving CSV file: " + err.getMessage());
                }
        );
    }

    // ==========================================
    // 5. Proctoring Review & Evidence Tab
    // ==========================================

    private Tab createProctoringTab() {
        Tab tab = new Tab("Proctoring & Evidence");
        VBox content = new VBox(14);
        content.setPadding(new Insets(20));

        // Top Filter Bar: Exam selection
        HBox filterBar = new HBox(12);
        filterBar.setAlignment(Pos.CENTER_LEFT);

        Label label = new Label("Select Exam:");
        label.setStyle("-fx-font-weight: bold;");

        proctorExamCombo.setPrefWidth(260);
        proctorExamCombo.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(AdminExamDto item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? "" : "#" + item.id() + " - " + item.title());
            }
        });
        proctorExamCombo.setButtonCell(new ListCell<>() {
            @Override
            protected void updateItem(AdminExamDto item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? "Select Exam..." : "#" + item.id() + " - " + item.title());
            }
        });
        proctorExamCombo.valueProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                loadProctorAttempts(newVal.id());
            }
        });

        Button refreshBtn = new Button("Refresh");
        refreshBtn.getStyleClass().add("btn-secondary");
        refreshBtn.setOnAction(e -> {
            AdminExamDto exam = proctorExamCombo.getValue();
            if (exam != null) {
                loadProctorAttempts(exam.id());
            }
        });

        Region filterSpacer = new Region();
        HBox.setHgrow(filterSpacer, Priority.ALWAYS);

        filterBar.getChildren().addAll(label, proctorExamCombo, refreshBtn, filterSpacer);

        // Main 2-column layout: Left (Attempts + Events), Right (Evidence Viewer)
        SplitPane mainSplit = new SplitPane();

        // Left Panel: Attempts list (top) and Events list (bottom)
        SplitPane leftSplit = new SplitPane();
        leftSplit.setOrientation(Orientation.VERTICAL);

        VBox attemptsBox = new VBox(8);
        Label attemptsTitle = new Label("1. Examination Attempts:");
        attemptsTitle.setFont(Font.font("Segoe UI", FontWeight.BOLD, 13));
        setupProctorAttemptsTable();
        attemptsBox.getChildren().addAll(attemptsTitle, proctorAttemptsTable);
        VBox.setVgrow(proctorAttemptsTable, Priority.ALWAYS);

        VBox eventsBox = new VBox(8);
        Label eventsTitle = new Label("2. Proctor Incidents for Selected Attempt:");
        eventsTitle.setFont(Font.font("Segoe UI", FontWeight.BOLD, 13));
        setupProctorEventsTable();
        eventsBox.getChildren().addAll(eventsTitle, proctorEventsTable);
        VBox.setVgrow(proctorEventsTable, Priority.ALWAYS);

        leftSplit.getItems().addAll(attemptsBox, eventsBox);
        leftSplit.setDividerPositions(0.48);

        // Right Panel: Evidence Viewer
        VBox rightPanel = new VBox(10);
        rightPanel.setPadding(new Insets(10));
        rightPanel.setAlignment(Pos.TOP_CENTER);
        rightPanel.getStyleClass().add("theme-card");

        HBox evidenceHeaderBox = new HBox(10);
        evidenceHeaderBox.setAlignment(Pos.CENTER_LEFT);
        Label evidenceHeader = new Label("3. Screenshot Evidence Inspection");
        evidenceHeader.setFont(Font.font("Segoe UI", FontWeight.BOLD, 14));
        evidenceHeaderBox.getChildren().add(evidenceHeader);

        // Viewer toolbar
        HBox viewerBar = new HBox(8);
        viewerBar.setAlignment(Pos.CENTER_LEFT);

        Button zoomInBtn = new Button("+ Zoom In");
        zoomInBtn.getStyleClass().add("btn-secondary");
        zoomInBtn.setOnAction(e -> zoomEvidence(1.25));

        Button zoomOutBtn = new Button("- Zoom Out");
        zoomOutBtn.getStyleClass().add("btn-secondary");
        zoomOutBtn.setOnAction(e -> zoomEvidence(0.8));

        Button resetZoomBtn = new Button("1:1 Reset");
        resetZoomBtn.getStyleClass().add("btn-secondary");
        resetZoomBtn.setOnAction(e -> resetEvidenceZoom());

        Button fitBtn = new Button("Fit Window");
        fitBtn.getStyleClass().add("btn-secondary");
        fitBtn.setOnAction(e -> fitEvidenceToWindow());

        evidenceZoomLabel.setStyle("-fx-font-weight: bold; -fx-min-width: 45px;");

        Region vSpacer = new Region();
        HBox.setHgrow(vSpacer, Priority.ALWAYS);

        Button prevEvBtn = new Button("← Prev");
        prevEvBtn.getStyleClass().add("btn-secondary");
        prevEvBtn.setOnAction(e -> navigateEvidence(-1));

        Button nextEvBtn = new Button("Next →");
        nextEvBtn.getStyleClass().add("btn-secondary");
        nextEvBtn.setOnAction(e -> navigateEvidence(1));

        Button enlargeBtn = new Button("🔍 Full View");
        enlargeBtn.getStyleClass().add("btn-primary");
        enlargeBtn.setOnAction(e -> showEnlargedEvidence());

        viewerBar.getChildren().addAll(zoomInBtn, zoomOutBtn, resetZoomBtn, fitBtn, evidenceZoomLabel, vSpacer, prevEvBtn, nextEvBtn, enlargeBtn);

        evidenceImageView.setPreserveRatio(true);
        evidenceImageView.setStyle("-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.15), 10, 0, 0, 2);");

        StackPane imageHolder = new StackPane(evidenceImageView);
        imageHolder.setAlignment(Pos.CENTER);
        imageHolder.setPadding(new Insets(10));

        evidenceScrollPane.setContent(imageHolder);
        evidenceScrollPane.setPannable(true);
        evidenceScrollPane.setFitToWidth(true);
        evidenceScrollPane.setFitToHeight(true);

        rightPanel.getChildren().addAll(evidenceHeaderBox, viewerBar, evidenceStatusLabel, evidenceScrollPane);
        VBox.setVgrow(evidenceScrollPane, Priority.ALWAYS);

        mainSplit.getItems().addAll(leftSplit, rightPanel);
        mainSplit.setDividerPositions(0.52);

        content.getChildren().addAll(filterBar, mainSplit);
        VBox.setVgrow(mainSplit, Priority.ALWAYS);
        tab.setContent(content);
        return tab;
    }

    private void setupProctorAttemptsTable() {
        proctorAttemptsTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        proctorAttemptsTable.setPlaceholder(new Label("Select an exam to load student attempts."));

        TableColumn<AdminAttemptDto, String> studentCol = new TableColumn<>("Student");
        studentCol.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().studentName()));

        TableColumn<AdminAttemptDto, String> enrollCol = new TableColumn<>("Enrollment");
        enrollCol.setCellValueFactory(d -> new SimpleStringProperty(
                d.getValue().enrollmentNumber() != null ? d.getValue().enrollmentNumber() : "-"));
        enrollCol.setMaxWidth(100);

        TableColumn<AdminAttemptDto, String> timeCol = new TableColumn<>("Attempt Time");
        timeCol.setCellValueFactory(d -> new SimpleStringProperty(
                d.getValue().startTime() != null ? d.getValue().startTime() : "-"));

        TableColumn<AdminAttemptDto, String> statusCol = new TableColumn<>("Status");
        statusCol.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().status()));
        statusCol.setMaxWidth(90);

        TableColumn<AdminAttemptDto, String> violCol = new TableColumn<>("Violations");
        violCol.setCellValueFactory(d -> new SimpleStringProperty(String.valueOf(d.getValue().focusLostCount())));
        violCol.setMaxWidth(80);

        TableColumn<AdminAttemptDto, String> procCol = new TableColumn<>("Integrity");
        procCol.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().flagged() ? "⚠️ FLAGGED" : "CLEAN"));
        procCol.setMaxWidth(110);
        procCol.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setStyle("");
                } else {
                    setText(item);
                    if (item.contains("FLAGGED")) {
                        setStyle("-fx-text-fill: #dc2626; -fx-font-weight: bold;");
                    } else {
                        setStyle("-fx-text-fill: #16a34a; -fx-font-weight: bold;");
                    }
                }
            }
        });

        TableColumn<AdminAttemptDto, String> scoreCol = new TableColumn<>("Score");
        scoreCol.setCellValueFactory(d -> new SimpleStringProperty(
                d.getValue().score() != null ? d.getValue().score() + " pts" : "Pending"));
        scoreCol.setMaxWidth(80);

        proctorAttemptsTable.getColumns().addAll(studentCol, enrollCol, timeCol, statusCol, violCol, procCol, scoreCol);

        proctorAttemptsTable.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, sel) -> {
            if (sel != null) {
                loadProctorEventsForAttempt(sel);
            } else {
                proctorEventsTable.getItems().clear();
                clearEvidenceViewer();
            }
        });
    }

    private void setupProctorEventsTable() {
        proctorEventsTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        proctorEventsTable.setPlaceholder(new Label("Select an attempt above to view proctor incidents."));

        TableColumn<AdminProctorEventDto, String> idCol = new TableColumn<>("ID");
        idCol.setCellValueFactory(d -> new SimpleStringProperty("#" + d.getValue().id()));
        idCol.setMaxWidth(70);

        TableColumn<AdminProctorEventDto, String> typeCol = new TableColumn<>("Incident Type");
        typeCol.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().eventType()));

        TableColumn<AdminProctorEventDto, String> timeCol = new TableColumn<>("Recorded Time");
        timeCol.setCellValueFactory(d -> new SimpleStringProperty(
                d.getValue().recordedAt() != null ? d.getValue().recordedAt() : "-"));

        TableColumn<AdminProctorEventDto, String> evCol = new TableColumn<>("Evidence");
        evCol.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().hasEvidence() ? "✓ Screenshot" : "None"));
        evCol.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setStyle("");
                } else {
                    setText(item);
                    if (item.contains("Screenshot")) {
                        setStyle("-fx-text-fill: #2563eb; -fx-font-weight: bold;");
                    } else {
                        setStyle("-fx-text-fill: #64748b;");
                    }
                }
            }
        });

        proctorEventsTable.getColumns().addAll(idCol, typeCol, timeCol, evCol);

        proctorEventsTable.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, sel) -> {
            if (sel != null) {
                loadEvidenceImage(sel);
            } else {
                clearEvidenceViewer();
            }
        });
    }

    private void loadProctorAttempts(int examId) {
        long reqId = ++currentProctorAttemptsReqId;
        proctorAttemptsTable.getItems().clear();
        proctorEventsTable.getItems().clear();
        clearEvidenceViewer();
        evidenceStatusLabel.setText("Loading student attempts for exam #" + examId + "...");

        AsyncRunner.runAsync(
                () -> apiClient.adminGetAttemptHistory(examId),
                (List<AdminAttemptDto> attempts) -> {
                    if (reqId != currentProctorAttemptsReqId) return;
                    evidenceStatusLabel.setText("Loaded " + (attempts != null ? attempts.size() : 0) + " attempt(s).");
                    if (attempts != null && !attempts.isEmpty()) {
                        proctorAttemptsTable.getItems().addAll(attempts);
                        AdminAttemptDto toSelect = attempts.stream().filter(AdminAttemptDto::flagged).findFirst().orElse(attempts.get(0));
                        proctorAttemptsTable.getSelectionModel().select(toSelect);
                    }
                },
                (Throwable err) -> {
                    if (reqId != currentProctorAttemptsReqId) return;
                    evidenceStatusLabel.setText("Failed to load attempts: " + err.getMessage());
                }
        );
    }

    private void loadProctorEventsForAttempt(AdminAttemptDto attempt) {
        int attemptId = attempt.id();
        long reqId = ++currentProctorEventsReqId;
        proctorEventsTable.getItems().clear();
        clearEvidenceViewer();
        evidenceCache.clear();
        evidenceStatusLabel.setText("Loading incidents for student " + attempt.studentName() + " (Attempt #" + attemptId + ")...");

        AsyncRunner.runAsync(
                () -> apiClient.adminGetProctorEvents(attemptId),
                (List<AdminProctorEventDto> events) -> {
                    if (reqId != currentProctorEventsReqId) return;
                    evidenceStatusLabel.setText("Loaded " + (events != null ? events.size() : 0) + " incident(s) for Attempt #" + attemptId);
                    if (events != null && !events.isEmpty()) {
                        proctorEventsTable.getItems().addAll(events);
                        for (AdminProctorEventDto ev : events) {
                            if (ev.hasEvidence()) {
                                proctorEventsTable.getSelectionModel().select(ev);
                                break;
                            }
                        }
                    }
                },
                (Throwable err) -> {
                    if (reqId != currentProctorEventsReqId) return;
                    evidenceStatusLabel.setText("Failed to load incidents: " + err.getMessage());
                }
        );
    }

    private void clearEvidenceViewer() {
        evidenceImageView.setImage(null);
        currentEvidenceBytes = null;
        currentEvidenceEventId = -1;
        evidenceZoom = 1.0;
        evidenceZoomLabel.setText("100%");
    }

    private void zoomEvidence(double factor) {
        if (evidenceImageView.getImage() == null) return;
        evidenceZoom = Math.max(0.25, Math.min(4.0, evidenceZoom * factor));
        applyEvidenceZoom();
    }

    private void resetEvidenceZoom() {
        if (evidenceImageView.getImage() == null) return;
        evidenceZoom = 1.0;
        applyEvidenceZoom();
    }

    private void fitEvidenceToWindow() {
        if (evidenceImageView.getImage() == null || evidenceScrollPane == null) return;
        Image img = evidenceImageView.getImage();
        double scrollW = evidenceScrollPane.getViewportBounds().getWidth();
        double scrollH = evidenceScrollPane.getViewportBounds().getHeight();
        if (scrollW > 20 && scrollH > 20 && img.getWidth() > 0 && img.getHeight() > 0) {
            double scaleW = (scrollW - 20) / img.getWidth();
            double scaleH = (scrollH - 20) / img.getHeight();
            evidenceZoom = Math.max(0.25, Math.min(4.0, Math.min(scaleW, scaleH)));
            applyEvidenceZoom();
        }
    }

    private void applyEvidenceZoom() {
        if (evidenceImageView.getImage() == null) return;
        Image img = evidenceImageView.getImage();
        evidenceImageView.setFitWidth(img.getWidth() * evidenceZoom);
        evidenceImageView.setFitHeight(img.getHeight() * evidenceZoom);
        evidenceZoomLabel.setText(String.format("%.0f%%", evidenceZoom * 100));
    }

    private void navigateEvidence(int direction) {
        int currentIndex = proctorEventsTable.getSelectionModel().getSelectedIndex();
        int targetIndex = currentIndex + direction;
        List<AdminProctorEventDto> items = proctorEventsTable.getItems();

        while (targetIndex >= 0 && targetIndex < items.size()) {
            if (items.get(targetIndex).hasEvidence()) {
                proctorEventsTable.getSelectionModel().select(targetIndex);
                return;
            }
            targetIndex += direction;
        }
        UiFeedback.showInfo(primaryStage, "Navigation", direction > 0 ? "No subsequent evidence events." : "No prior evidence events.");
    }

    private void loadEvidenceImage(AdminProctorEventDto event) {
        int eventId = event.id();
        long reqId = ++currentEvidenceReqId;

        if (!event.hasEvidence()) {
            clearEvidenceViewer();
            evidenceStatusLabel.setText("Event #" + eventId + " (" + event.eventType() + ") has no screenshot evidence.");
            return;
        }

        byte[] cached = evidenceCache.get(eventId);
        if (cached != null) {
            displayEvidenceBytes(eventId, cached);
            return;
        }

        evidenceStatusLabel.setText("Fetching screenshot bytes for Event #" + eventId + "...");
        AsyncRunner.runAsync(
                () -> apiClient.adminGetEvidence(eventId),
                (byte[] imageBytes) -> {
                    if (reqId != currentEvidenceReqId) return;
                    AdminProctorEventDto currentSel = proctorEventsTable.getSelectionModel().getSelectedItem();
                    if (currentSel == null || currentSel.id() != eventId) return;

                    if (imageBytes == null || imageBytes.length == 0) {
                        clearEvidenceViewer();
                        evidenceStatusLabel.setText("Evidence file is empty for Event #" + eventId);
                        return;
                    }

                    evidenceCache.put(eventId, imageBytes);
                    displayEvidenceBytes(eventId, imageBytes);
                },
                (Throwable err) -> {
                    if (reqId != currentEvidenceReqId) return;
                    evidenceStatusLabel.setText("Failed to retrieve screenshot: " + err.getMessage());
                }
        );
    }

    private void displayEvidenceBytes(int eventId, byte[] imageBytes) {
        try {
            currentEvidenceBytes = imageBytes;
            currentEvidenceEventId = eventId;
            Image img = new Image(new ByteArrayInputStream(imageBytes));
            evidenceImageView.setImage(img);
            resetEvidenceZoom();
            evidenceStatusLabel.setText("Event #" + eventId + " evidence loaded (" + imageBytes.length + " bytes).");
        } catch (Exception e) {
            evidenceStatusLabel.setText("Failed to decode screenshot image: " + e.getMessage());
        }
    }

    private void showEnlargedEvidence() {
        AdminProctorEventDto sel = proctorEventsTable.getSelectionModel().getSelectedItem();
        if (sel == null || currentEvidenceBytes == null || currentEvidenceBytes.length == 0 || currentEvidenceEventId != sel.id()) {
            UiFeedback.showError(primaryStage, "No Evidence", "Please select an event with screenshot evidence loaded to inspect.");
            return;
        }

        Stage viewerStage = new Stage();
        viewerStage.initOwner(primaryStage);
        viewerStage.initModality(Modality.NONE);
        viewerStage.setTitle("Evidence Inspector — Event #" + currentEvidenceEventId + " (" + sel.eventType() + ")");

        BorderPane modalRoot = new BorderPane();
        modalRoot.setStyle("-fx-background-color: #0f172a;");

        HBox bar = new HBox(10);
        bar.setPadding(new Insets(10, 16, 10, 16));
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.setStyle("-fx-background-color: #1e293b; -fx-border-color: #334155; -fx-border-width: 0 0 1 0;");

        Label title = new Label("Event #" + currentEvidenceEventId + " (" + sel.eventType() + ") Evidence Inspection");
        title.setStyle("-fx-text-fill: white; -fx-font-weight: bold; -fx-font-size: 14px;");

        Region barSpacer = new Region();
        HBox.setHgrow(barSpacer, Priority.ALWAYS);

        final double[] modalZoom = {1.0};
        Label modalZoomLbl = new Label("100%");
        modalZoomLbl.setStyle("-fx-text-fill: #94a3b8; -fx-min-width: 50px;");

        Image img = new Image(new ByteArrayInputStream(currentEvidenceBytes));
        ImageView modalImageView = new ImageView(img);
        modalImageView.setPreserveRatio(true);

        Runnable applyModalZoom = () -> {
            modalImageView.setFitWidth(img.getWidth() * modalZoom[0]);
            modalImageView.setFitHeight(img.getHeight() * modalZoom[0]);
            modalZoomLbl.setText(String.format("%.0f%%", modalZoom[0] * 100));
        };

        Button zIn = new Button("+ Zoom In");
        zIn.setStyle("-fx-background-color: #334155; -fx-text-fill: white; -fx-border-color: #475569; -fx-border-radius: 4px;");
        zIn.setOnAction(e -> { modalZoom[0] = Math.min(4.0, modalZoom[0] * 1.25); applyModalZoom.run(); });

        Button zOut = new Button("- Zoom Out");
        zOut.setStyle("-fx-background-color: #334155; -fx-text-fill: white; -fx-border-color: #475569; -fx-border-radius: 4px;");
        zOut.setOnAction(e -> { modalZoom[0] = Math.max(0.25, modalZoom[0] / 1.25); applyModalZoom.run(); });

        Button zReset = new Button("1:1 Reset");
        zReset.setStyle("-fx-background-color: #334155; -fx-text-fill: white; -fx-border-color: #475569; -fx-border-radius: 4px;");
        zReset.setOnAction(e -> { modalZoom[0] = 1.0; applyModalZoom.run(); });

        Button fitBtn = new Button("Fit Window");
        fitBtn.setStyle("-fx-background-color: #334155; -fx-text-fill: white; -fx-border-color: #475569; -fx-border-radius: 4px;");

        Button closeBtn = new Button("Close");
        closeBtn.setStyle("-fx-background-color: #ef4444; -fx-text-fill: white; -fx-font-weight: bold; -fx-background-radius: 4px;");
        closeBtn.setOnAction(e -> viewerStage.close());

        bar.getChildren().addAll(title, barSpacer, zIn, zOut, zReset, fitBtn, modalZoomLbl, closeBtn);
        modalRoot.setTop(bar);

        StackPane imgCenter = new StackPane(modalImageView);
        imgCenter.setPadding(new Insets(20));
        imgCenter.setAlignment(Pos.CENTER);
        ScrollPane modalScroll = new ScrollPane(imgCenter);
        modalScroll.setPannable(true);
        modalScroll.setFitToWidth(false);
        modalScroll.setFitToHeight(false);
        modalScroll.setStyle("-fx-background: transparent; -fx-background-color: transparent;");
        modalRoot.setCenter(modalScroll);

        fitBtn.setOnAction(e -> {
            double sw = modalScroll.getViewportBounds().getWidth() - 40;
            double sh = modalScroll.getViewportBounds().getHeight() - 40;
            if (sw > 50 && sh > 50 && img.getWidth() > 0 && img.getHeight() > 0) {
                modalZoom[0] = Math.max(0.25, Math.min(4.0, Math.min(sw / img.getWidth(), sh / img.getHeight())));
                applyModalZoom.run();
            }
        });

        Scene scene = new Scene(modalRoot, 1100, 750);
        com.examsystem.client.theme.ThemeManager.getInstance().applyTheme(scene);
        viewerStage.setScene(scene);
        viewerStage.show();
        Platform.runLater(fitBtn::fire);
    }
}
