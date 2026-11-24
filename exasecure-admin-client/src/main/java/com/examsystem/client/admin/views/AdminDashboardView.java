package com.examsystem.client.admin.views;

import com.examsystem.client.admin.AdminApp;
import com.examsystem.client.api.ApiClient;
import com.examsystem.client.async.AsyncRunner;
import com.examsystem.client.ui.AppIcon;
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
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.io.ByteArrayInputStream;
import java.io.File;
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
    private final Label evidenceContextLabel = new Label("No attempt or incident selected.");
    private final Label evidenceZoomLabel = new Label("100%");
    private double evidenceZoom = 1.0;
    private boolean evidenceFitMode = true;
    private final Map<String, byte[]> evidenceCache = new HashMap<>();
    private final ComboBox<EvidenceChoice> evidenceTypeCombo = new ComboBox<>();
    private boolean updatingEvidenceChoices;
    private String currentEvidenceType = "INITIAL";
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
        view.getStyleClass().add("app-root");
        for (Label status : List.of(usersStatusLabel, examsStatusLabel, qbStatusLabel,
                attemptsStatusLabel, evidenceStatusLabel)) {
            status.getStyleClass().add("status-text");
            status.setWrapText(true);
        }

        // Top Navigation Bar
        VBox topContainer = new VBox();

        HBox topBar = new HBox(16);
        topBar.setPadding(new Insets(14, 24, 14, 24));
        topBar.setAlignment(Pos.CENTER_LEFT);
        topBar.getStyleClass().addAll("app-header", "app-header-admin");

        Label brand = new Label("ExaSecure");
        brand.getStyleClass().add("header-title");

        Label adminBadge = new Label("ADMIN CONSOLE");
        adminBadge.getStyleClass().addAll("portal-badge", "portal-badge-admin");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        String adminName = apiClient.getSessionState().fullName() != null
                ? apiClient.getSessionState().fullName()
                : apiClient.getSessionState().username();
        Label userLabel = new Label("Admin: " + adminName);
        userLabel.getStyleClass().add("header-meta");

        Button logoutBtn = new Button("Sign Out");
        logoutBtn.getStyleClass().add("btn-header");
        logoutBtn.setOnAction(e -> com.examsystem.client.ui.SignOutAction.run(apiClient, view, app::showLogin));

        var themeSelector = com.examsystem.client.theme.ThemeManager.getInstance().createThemeSelector();
        topBar.getChildren().addAll(brand, adminBadge, spacer, themeSelector, userLabel, logoutBtn);

        // Command & Overview Strip
        HBox overviewStrip = new HBox(20);
        overviewStrip.setPadding(new Insets(8, 24, 8, 24));
        overviewStrip.setAlignment(Pos.CENTER_LEFT);
        overviewStrip.getStyleClass().add("status-strip");

        metricUsersLabel.getStyleClass().add("small-text");
        metricExamsLabel.getStyleClass().add("small-text");
        metricAttemptsLabel.getStyleClass().add("small-text");

        Region stripSpacer = new Region();
        HBox.setHgrow(stripSpacer, Priority.ALWAYS);

        Label quickLabel = new Label("Quick Actions:");
        quickLabel.getStyleClass().addAll("secondary-text", "small-text");

        Button quickAddExamBtn = new Button("+ New Exam");
        quickAddExamBtn.getStyleClass().add("btn-primary");
        quickAddExamBtn.setOnAction(e -> showCreateExamDialog());

        Button quickAddUserBtn = new Button("+ New User");
        quickAddUserBtn.getStyleClass().add("btn-primary");
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

        FlowPane toolbar = new FlowPane(Orientation.HORIZONTAL, 10, 8);
        toolbar.setAlignment(Pos.CENTER_LEFT);
        toolbar.setPrefWrapLength(1000);

        Label title = new Label("System Users");
        title.getStyleClass().add("section-title");

        userSearchField.setPromptText("Search by name, username, or enrollment...");
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

        toolbar.getChildren().addAll(title, userSearchField, addBtn, editBtn, deactivateBtn, reactivateBtn, deleteBtn, refreshBtn);

        setupUsersTable();

        content.getChildren().addAll(toolbar, usersStatusLabel, usersTable);
        VBox.setVgrow(usersTable, Priority.ALWAYS);
        tab.setContent(content);
        return tab;
    }

    private void setupUsersTable() {
        usersTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        usersTable.setPlaceholder(new Label("No user accounts found."));

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
        statusCol.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().isActive() ? "ACTIVE" : "INACTIVE"));
        statusCol.setPrefWidth(100);
        statusCol.setCellFactory(col -> createBadgeCell());

        usersTable.getColumns().addAll(List.of(nameCol, usernameCol, enrollCol, secCol, roleCol, statusCol));
    }

    private void loadUsers() {
        usersTable.getItems().clear();
        allUsersList.clear();
        setStatus(usersStatusLabel, "Loading user accounts from server...", null);
        AsyncRunner.runAsync(
                apiClient::adminListUsers,
                (List<AdminUserDto> users) -> {
                    setStatus(usersStatusLabel, "", null);
                    if (users != null) {
                        allUsersList.addAll(users);
                        filterUsers(userSearchField.getText());
                        metricUsersLabel.setText("Users: " + users.size());
                    }
                },
                (Throwable err) -> setStatus(usersStatusLabel,
                        "Unable to load user accounts. Check the server connection and retry.", "status-error")
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

        FlowPane toolbar = new FlowPane(Orientation.HORIZONTAL, 10, 8);
        toolbar.setAlignment(Pos.CENTER_LEFT);
        toolbar.setPrefWrapLength(1000);

        Label title = new Label("Exam Definitions");
        title.getStyleClass().add("section-title");

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

        toolbar.getChildren().addAll(title, createBtn, reviseBtn, deactivateBtn, reactivateBtn, refreshBtn);

        setupExamsTable();

        content.getChildren().addAll(toolbar, examsStatusLabel, examsTable);
        VBox.setVgrow(examsTable, Priority.ALWAYS);
        tab.setContent(content);
        return tab;
    }

    private void setupExamsTable() {
        examsTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        examsTable.setPlaceholder(new Label("No examinations defined in the system."));

        TableColumn<AdminExamDto, String> titleCol = new TableColumn<>("Title");
        titleCol.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().title()));

        TableColumn<AdminExamDto, String> durCol = new TableColumn<>("Duration (mins)");
        durCol.setCellValueFactory(d -> new SimpleStringProperty(String.valueOf(d.getValue().durationMinutes())));

        TableColumn<AdminExamDto, String> verCol = new TableColumn<>("Version");
        verCol.setCellValueFactory(d -> new SimpleStringProperty("v" + d.getValue().versionNumber()));

        TableColumn<AdminExamDto, String> activeCol = new TableColumn<>("Status");
        activeCol.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().isActive() ? "ACTIVE" : "INACTIVE"));
        activeCol.setCellFactory(col -> createBadgeCell());

        examsTable.getColumns().addAll(List.of(titleCol, durCol, verCol, activeCol));
    }

    private void loadExams() {
        loadExams(-1);
    }

    private void loadExams(int reselectExamId) {
        examsTable.getItems().clear();
        allExamsList.clear();
        setStatus(examsStatusLabel, "Loading examinations from server...", null);
        AsyncRunner.runAsync(
                apiClient::adminListExams,
                (List<AdminExamDto> exams) -> {
                    setStatus(examsStatusLabel, "", null);
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
                (Throwable err) -> setStatus(examsStatusLabel,
                        "Unable to load examinations. Check the server connection and retry.", "status-error")
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
                        UiFeedback.showInfo(primaryStage, "Exam Created", "The examination was created successfully as version " + res.versionNumber() + ".");
                        loadExams(res.id());
                    },
                    (Throwable err) -> UiFeedback.showError(primaryStage, "Creation Failed", err.getMessage())
            );
        });
    }

    private void showReviseExamDialog(AdminExamDto exam) {
        setStatus(examsStatusLabel, "Loading questions for “" + exam.title() + "”...", null);
        AsyncRunner.runAsync(
                () -> apiClient.adminGetExamQuestions(exam.id()),
                (List<AdminQuestionDefinitionDto> questions) -> {
                    setStatus(examsStatusLabel, "", null);
                    ExamDefinitionBuilderDialog builder = new ExamDefinitionBuilderDialog(
                            "Revise Examination (v" + (exam.versionNumber() + 1) + ")",
                            "Create a new version while preserving completed attempts from the current version:",
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
                                    UiFeedback.showInfo(primaryStage, "Exam Revised", "The new examination version " + res.versionNumber() + " was created successfully.");
                                    loadExams(res.id());
                                },
                                (Throwable err) -> UiFeedback.showError(primaryStage, "Revision Failed", err.getMessage())
                        );
                    });
                },
                (Throwable err) -> {
                    setStatus(examsStatusLabel, "", null);
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
        examSelectLabel.getStyleClass().add("field-label");

        questionBankExamCombo.setPrefWidth(280);
        questionBankExamCombo.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(AdminExamDto item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? "" : item.title() + " (v" + item.versionNumber() + ")");
            }
        });
        questionBankExamCombo.setButtonCell(new ListCell<>() {
            @Override
            protected void updateItem(AdminExamDto item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? "Select examination" : item.title() + " (v" + item.versionNumber() + ")");
            }
        });
        questionBankExamCombo.valueProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) loadQuestionBank(newVal.id());
        });

        Button loadQbBtn = new Button("Load Questions");
        loadQbBtn.getStyleClass().add("btn-primary");
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
        right.getStyleClass().addAll("theme-card", "details-panel");

        Label detailsTitle = new Label("Question & Answer Key Preview");
        detailsTitle.getStyleClass().add("section-title");

        ScrollPane optScroll = new ScrollPane(qbOptionsDetailsBox);
        optScroll.setFitToWidth(true);

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

        qbQuestionsTable.getColumns().addAll(List.of(numCol, textCol, typeCol, optCountCol));

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
        AdminExamDto selectedExam = questionBankExamCombo.getValue();
        String selectedTitle = selectedExam != null ? selectedExam.title() : "selected examination";
        setStatus(qbStatusLabel, "Loading question bank for “" + selectedTitle + "”...", null);

        AsyncRunner.runAsync(
                () -> apiClient.adminGetExamQuestions(examId),
                (List<AdminQuestionDefinitionDto> questions) -> {
                    if (reqId != currentQbReqId) return;
                    AdminExamDto curExam = questionBankExamCombo.getValue();
                    if (curExam != null && curExam.id() != examId) return;

                    setStatus(qbStatusLabel, "", null);
                    if (questions != null) {
                        qbQuestionsTable.getItems().setAll(questions);
                        if (!questions.isEmpty()) {
                            qbQuestionsTable.getSelectionModel().select(0);
                        }
                    }
                },
                (Throwable err) -> {
                    if (reqId != currentQbReqId) return;
                    setStatus(qbStatusLabel,
                            "Unable to load the question bank. Check the server connection and retry.", "status-error");
                }
        );
    }

    private void renderQuestionBankDetails(AdminQuestionDefinitionDto q) {
        qbOptionsDetailsBox.getChildren().clear();

        Label qLabel = new Label(q.text());
        qLabel.getStyleClass().add("theme-label-bold");
        qLabel.setWrapText(true);

        Label typeBadge = new Label("Type: " + q.type());
        typeBadge.getStyleClass().add("badge-neutral");

        qbOptionsDetailsBox.getChildren().addAll(qLabel, typeBadge, new Separator());

        Label optionsHeader = new Label("Options & Answer Keys:");
        optionsHeader.getStyleClass().add("field-label");
        qbOptionsDetailsBox.getChildren().add(optionsHeader);

        int optNum = 1;
        for (AdminOptionDefinitionDto opt : q.options()) {
            HBox optRow = new HBox(10);
            optRow.setAlignment(Pos.CENTER_LEFT);
            optRow.setPadding(new Insets(8, 12, 8, 12));
            optRow.getStyleClass().add(opt.correct() ? "correct-option" : "incorrect-option");

            Label optNumLabel = new Label(optNum + ".");
            optNumLabel.getStyleClass().add("theme-label-bold");

            Label optTextLabel = new Label(opt.text());
            optTextLabel.setWrapText(true);

            Region rowSpacer = new Region();
            HBox.setHgrow(rowSpacer, Priority.ALWAYS);

            Label badge = new Label(opt.correct() ? "✔ CORRECT" : "✘ Incorrect");
            badge.getStyleClass().add(opt.correct() ? "badge-success" : "badge-neutral");

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

        FlowPane filterBar = new FlowPane(Orientation.HORIZONTAL, 10, 8);
        filterBar.setAlignment(Pos.CENTER_LEFT);
        filterBar.setPrefWrapLength(1060);

        Label label = new Label("Select Exam:");
        label.getStyleClass().add("field-label");

        attemptsExamCombo.setPrefWidth(240);
        attemptsExamCombo.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(AdminExamDto item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? "" : item.title() + " (v" + item.versionNumber() + ")");
            }
        });
        attemptsExamCombo.setButtonCell(new ListCell<>() {
            @Override
            protected void updateItem(AdminExamDto item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? "Select examination" : item.title() + " (v" + item.versionNumber() + ")");
            }
        });
        attemptsExamCombo.valueProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                loadAttempts();
            }
        });

        Button loadBtn = new Button("Load Attempts");
        loadBtn.getStyleClass().add("btn-primary");
        loadBtn.setOnAction(e -> loadAttempts());

        Region filterSpacer = new Region();
        HBox.setHgrow(filterSpacer, Priority.ALWAYS);

        Button exportCsvBtn = new Button("📥 Export to CSV");
        exportCsvBtn.setText("Export to CSV");
        exportCsvBtn.getStyleClass().add("btn-secondary");
        exportCsvBtn.setOnAction(e -> exportAttemptsToCsv());

        Button viewDetailsBtn = new Button("📋 View Report Card");
        viewDetailsBtn.setText("View Report Card");
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
        statsBtn.setText("Exam Analytics");
        statsBtn.getStyleClass().add("btn-secondary");
        statsBtn.setOnAction(e -> openExamStatistics());

        Button inspectProctorBtn = new Button("🛡️ Inspect Proctor Events");
        inspectProctorBtn.setText("Inspect Proctor Events");
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
        deleteAttemptBtn.setText("Delete Attempt");
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

        filterBar.getChildren().addAll(label, attemptsExamCombo, loadBtn, exportCsvBtn, viewDetailsBtn, statsBtn, inspectProctorBtn, deleteAttemptBtn);

        setupAttemptsTable();

        content.getChildren().addAll(filterBar, attemptsStatusLabel, attemptsTable);
        VBox.setVgrow(attemptsTable, Priority.ALWAYS);
        tab.setContent(content);
        return tab;
    }

    private void setupAttemptsTable() {
        attemptsTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        attemptsTable.setPlaceholder(new Label("Select an examination to view its attempts."));

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
        statusCol.setCellFactory(col -> createBadgeCell());

        TableColumn<AdminAttemptDto, String> scoreCol = new TableColumn<>("Score");
        scoreCol.setCellValueFactory(d -> new SimpleStringProperty(
                d.getValue().score() != null ? String.valueOf(d.getValue().score()) : "Pending"));

        TableColumn<AdminAttemptDto, String> violationsCol = new TableColumn<>("Violations");
        violationsCol.setCellValueFactory(d -> new SimpleStringProperty(String.valueOf(d.getValue().focusLostCount())));
        violationsCol.setMaxWidth(90);

        TableColumn<AdminAttemptDto, String> proctoringCol = new TableColumn<>("Proctoring");
        proctoringCol.setCellValueFactory(d -> new SimpleStringProperty(
                d.getValue().flagged() ? "⚠️ FLAGGED" : "CLEAN"));
        proctoringCol.setPrefWidth(120);
        proctoringCol.setCellFactory(col -> createBadgeCell());

        attemptsTable.getColumns().addAll(List.of(nameCol, enrollCol, startCol, statusCol, scoreCol, violationsCol, proctoringCol));

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
        AdminExamDto selectedExam = attemptsExamCombo.getValue();
        if (selectedExam == null) {
            setStatus(attemptsStatusLabel, "Select an examination to load attempts.", null);
            return;
        }
        int examId = selectedExam.id();

        long reqId = ++currentAttemptsReqId;
        attemptsTable.getItems().clear();
        setStatus(attemptsStatusLabel, "Loading attempts for “" + selectedExam.title() + "”...", null);
        AsyncRunner.runAsync(
                () -> apiClient.adminGetAttemptHistory(examId),
                (List<AdminAttemptDto> attempts) -> {
                    if (reqId != currentAttemptsReqId) return;
                    AdminExamDto currentExam = attemptsExamCombo.getValue();
                    if (currentExam == null || currentExam.id() != examId) return;

                    setStatus(attemptsStatusLabel, "", null);
                    if (attempts != null) {
                        attemptsTable.getItems().addAll(attempts);
                        metricAttemptsLabel.setText("Attempts: " + attempts.size() + " for " + selectedExam.title());
                    }
                },
                (Throwable err) -> {
                    if (reqId != currentAttemptsReqId) return;
                    setStatus(attemptsStatusLabel,
                            "Unable to load attempts. Check the server connection and retry.", "status-error");
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
                        + "This permanently removes the attempt, its answers, and its proctoring evidence.\n\n"
                        + ("ACTIVE".equals(attempt.status())
                        ? "This attempt is in progress. The student can start again if the exam is open to new starts."
                        : "This also removes the recorded attempt from exam history.")
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
        AdminExamDto selectedExam = attemptsExamCombo.getValue();
        if (selectedExam == null) {
            UiFeedback.showError(primaryStage, "Selection Required", "Please select an exam first to view statistics.");
            return;
        }
        int examId = selectedExam.id();

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

        AdminExamDto selectedExam = attemptsExamCombo.getValue();
        String examId = selectedExam != null ? String.valueOf(selectedExam.id()) : "all";
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Export Attempts to CSV");
        fileChooser.setInitialFileName("attempts-exam-" + examId + ".csv");
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV Files (*.csv)", "*.csv"));

        File file = fileChooser.showSaveDialog(view.getScene() != null ? view.getScene().getWindow() : null);
        if (file == null) return;

        attemptsStatusLabel.setText("Exporting attempts to CSV...");
        AsyncRunner.runAsync(
                () -> {
                    CsvExport.write(file.toPath(), writer -> {
                        writer.write("Attempt ID,Student ID,Student Name,Enrollment,Start Time,Status,Score,Violations,Flagged,Proctoring Status\n");
                        for (AdminAttemptDto a : items) {
                            CsvExport.checkInterrupted();
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
                    });
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
        label.getStyleClass().add("field-label");

        proctorExamCombo.setPrefWidth(260);
        proctorExamCombo.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(AdminExamDto item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? "" : item.title() + " (v" + item.versionNumber() + ")");
            }
        });
        proctorExamCombo.setButtonCell(new ListCell<>() {
            @Override
            protected void updateItem(AdminExamDto item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? "Select examination" : item.title() + " (v" + item.versionNumber() + ")");
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
        attemptsTitle.getStyleClass().add("workflow-title");
        setupProctorAttemptsTable();
        attemptsBox.getChildren().addAll(attemptsTitle, proctorAttemptsTable);
        VBox.setVgrow(proctorAttemptsTable, Priority.ALWAYS);

        VBox eventsBox = new VBox(8);
        Label eventsTitle = new Label("2. Proctor Incidents for Selected Attempt:");
        eventsTitle.getStyleClass().add("workflow-title");
        setupProctorEventsTable();
        eventsBox.getChildren().addAll(eventsTitle, proctorEventsTable);
        VBox.setVgrow(proctorEventsTable, Priority.ALWAYS);

        leftSplit.getItems().addAll(attemptsBox, eventsBox);
        leftSplit.setDividerPositions(0.48);

        // Right Panel: Evidence Viewer
        VBox rightPanel = new VBox(10);
        rightPanel.setPadding(new Insets(10));
        rightPanel.setAlignment(Pos.TOP_CENTER);
        rightPanel.getStyleClass().addAll("theme-card", "evidence-panel");

        HBox evidenceHeaderBox = new HBox(10);
        evidenceHeaderBox.setAlignment(Pos.CENTER_LEFT);
        Label evidenceHeader = new Label("3. Screenshot Evidence Inspection");
        evidenceHeader.getStyleClass().add("workflow-title-large");
        evidenceHeaderBox.getChildren().add(evidenceHeader);

        // Viewer toolbar
        HBox viewerBar = new HBox(8);
        viewerBar.setAlignment(Pos.CENTER_LEFT);

        Label evTypeLabel = new Label("Evidence:");
        evTypeLabel.getStyleClass().add("theme-label-bold");

        evidenceTypeCombo.setOnAction(e -> {
            if (updatingEvidenceChoices) return;
            AdminProctorEventDto sel = proctorEventsTable.getSelectionModel().getSelectedItem();
            if (sel != null) {
                loadEvidenceImage(sel);
            }
        });

        Button zoomInBtn = new Button("+ Zoom In");
        zoomInBtn.getStyleClass().add("btn-secondary");
        zoomInBtn.setOnAction(e -> zoomEvidence(1.25));

        Button zoomOutBtn = new Button("- Zoom Out");
        zoomOutBtn.getStyleClass().add("btn-secondary");
        zoomOutBtn.setOnAction(e -> zoomEvidence(0.8));

        Button resetZoomBtn = new Button("100%");
        resetZoomBtn.getStyleClass().add("btn-secondary");
        resetZoomBtn.setOnAction(e -> resetEvidenceZoom());

        Button fitBtn = new Button("Fit");
        fitBtn.getStyleClass().add("btn-secondary");
        fitBtn.setOnAction(e -> fitEvidenceToWindow());

        evidenceZoomLabel.getStyleClass().add("theme-label-bold");
        evidenceZoomLabel.setMinWidth(52);

        Region vSpacer = new Region();
        HBox.setHgrow(vSpacer, Priority.ALWAYS);

        Button prevEvBtn = new Button("← Prev");
        prevEvBtn.setText("Previous");
        prevEvBtn.getStyleClass().add("btn-secondary");
        prevEvBtn.setOnAction(e -> navigateEvidence(-1));

        Button nextEvBtn = new Button("Next →");
        nextEvBtn.setText("Next");
        nextEvBtn.getStyleClass().add("btn-secondary");
        nextEvBtn.setOnAction(e -> navigateEvidence(1));

        Button enlargeBtn = new Button("🔍 Full View");
        enlargeBtn.setText("Enlarge");
        enlargeBtn.getStyleClass().add("btn-primary");
        enlargeBtn.setOnAction(e -> showEnlargedEvidence());

        viewerBar.getChildren().addAll(evTypeLabel, evidenceTypeCombo, zoomInBtn, zoomOutBtn, resetZoomBtn, fitBtn, evidenceZoomLabel, vSpacer, prevEvBtn, nextEvBtn, enlargeBtn);

        evidenceImageView.setPreserveRatio(true);

        StackPane imageHolder = new StackPane(evidenceImageView);
        imageHolder.setAlignment(Pos.CENTER);
        imageHolder.setPadding(new Insets(10));
        imageHolder.getStyleClass().add("evidence-canvas");

        evidenceScrollPane.setContent(imageHolder);
        evidenceScrollPane.setPannable(true);
        evidenceScrollPane.setFitToWidth(true);
        evidenceScrollPane.setFitToHeight(true);
        evidenceScrollPane.viewportBoundsProperty().addListener((obs, oldBounds, newBounds) -> {
            if (evidenceFitMode) fitEvidenceToWindow();
        });

        evidenceContextLabel.getStyleClass().addAll("secondary-text", "small-text");
        evidenceContextLabel.setWrapText(true);
        rightPanel.getChildren().addAll(evidenceHeaderBox, viewerBar, evidenceContextLabel, evidenceStatusLabel, evidenceScrollPane);
        VBox.setVgrow(evidenceScrollPane, Priority.ALWAYS);

        mainSplit.getItems().addAll(leftSplit, rightPanel);
        mainSplit.setDividerPositions(0.44);

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
        statusCol.setCellFactory(col -> createBadgeCell());

        TableColumn<AdminAttemptDto, String> violCol = new TableColumn<>("Violations");
        violCol.setCellValueFactory(d -> new SimpleStringProperty(String.valueOf(d.getValue().focusLostCount())));
        violCol.setMaxWidth(80);

        TableColumn<AdminAttemptDto, String> procCol = new TableColumn<>("Integrity");
        procCol.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().flagged() ? "⚠️ FLAGGED" : "CLEAN"));
        procCol.setMaxWidth(110);
        procCol.setCellFactory(col -> createBadgeCell());

        TableColumn<AdminAttemptDto, String> scoreCol = new TableColumn<>("Score");
        scoreCol.setCellValueFactory(d -> new SimpleStringProperty(
                d.getValue().score() != null ? String.valueOf(d.getValue().score()) : "Pending"));
        scoreCol.setMaxWidth(80);

        proctorAttemptsTable.getColumns().addAll(List.of(studentCol, enrollCol, timeCol, statusCol, violCol, procCol, scoreCol));

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

        TableColumn<AdminProctorEventDto, String> typeCol = new TableColumn<>("Incident Type");
        typeCol.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().eventType()));
        typeCol.setPrefWidth(110);

        TableColumn<AdminProctorEventDto, String> timeCol = new TableColumn<>("Recorded Time");
        timeCol.setCellValueFactory(d -> new SimpleStringProperty(
                d.getValue().recordedAt() != null ? d.getValue().recordedAt() : "-"));
        timeCol.setPrefWidth(140);

        TableColumn<AdminProctorEventDto, String> durationCol = new TableColumn<>("Away Duration");
        durationCol.setCellValueFactory(d -> {
            Long dur = d.getValue().awayDurationMs();
            if (dur == null) {
                return new SimpleStringProperty(d.getValue().returnedAt() != null ? "<1s" : "-");
            }
            if (dur < 1000) {
                return new SimpleStringProperty(dur + "ms");
            }
            return new SimpleStringProperty(String.format(java.util.Locale.ROOT, "%.1fs", dur / 1000.0));
        });
        durationCol.setPrefWidth(100);

        TableColumn<AdminProctorEventDto, String> longAbsenceCol = new TableColumn<>("Absence");
        longAbsenceCol.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().isLongAbsence() ? "LONG (≥5s)" : "NORMAL"));
        longAbsenceCol.setCellFactory(col -> new TableCell<>() {
            private final Label badge = new Label();
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                setText(null);
                if (empty || item == null) {
                    setGraphic(null);
                } else {
                    badge.setText(item);
                    badge.getStyleClass().setAll(item.contains("LONG") ? "badge-warning" : "badge-neutral");
                    setGraphic(badge);
                }
            }
        });
        longAbsenceCol.setPrefWidth(100);

        TableColumn<AdminProctorEventDto, String> evCol = new TableColumn<>("Evidence");
        evCol.setCellValueFactory(d -> {
            List<String> types = d.getValue().evidenceTypes();
            if (types == null || types.isEmpty()) {
                return new SimpleStringProperty("NONE");
            }
            return new SimpleStringProperty(EvidenceChoice.forTypes(types).stream().map(Object::toString).collect(java.util.stream.Collectors.joining(" + ")));
        });
        evCol.setCellFactory(col -> new TableCell<>() {
            private final Label badge = new Label();

            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                setText(null);
                if (empty || item == null) {
                    setGraphic(null);
                } else {
                    boolean hasAny = !item.equals("NONE");
                    badge.setText(item);
                    badge.getStyleClass().setAll(hasAny ? "badge-info" : "badge-neutral");
                    setGraphic(badge);
                }
            }
        });
        evCol.setPrefWidth(140);

        proctorEventsTable.getColumns().addAll(List.of(typeCol, timeCol, durationCol, longAbsenceCol, evCol));

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
        AdminExamDto selectedExam = proctorExamCombo.getValue();
        String selectedTitle = selectedExam != null ? selectedExam.title() : "selected examination";
        setStatus(evidenceStatusLabel, "Loading student attempts for “" + selectedTitle + "”...", null);

        AsyncRunner.runAsync(
                () -> apiClient.adminGetAttemptHistory(examId),
                (List<AdminAttemptDto> attempts) -> {
                    if (reqId != currentProctorAttemptsReqId) return;
                    setStatus(evidenceStatusLabel,
                            "Loaded " + (attempts != null ? attempts.size() : 0) + " attempt(s).", "status-success");
                    if (attempts != null && !attempts.isEmpty()) {
                        proctorAttemptsTable.getItems().addAll(attempts);
                        AdminAttemptDto toSelect = attempts.stream().filter(AdminAttemptDto::flagged).findFirst().orElse(attempts.get(0));
                        proctorAttemptsTable.getSelectionModel().select(toSelect);
                    }
                },
                (Throwable err) -> {
                    if (reqId != currentProctorAttemptsReqId) return;
                    setStatus(evidenceStatusLabel,
                            "Unable to load attempts for evidence review. Check the server connection and retry.", "status-error");
                }
        );
    }

    private void loadProctorEventsForAttempt(AdminAttemptDto attempt) {
        int attemptId = attempt.id();
        long reqId = ++currentProctorEventsReqId;
        proctorEventsTable.getItems().clear();
        clearEvidenceViewer();
        evidenceCache.clear();
        updateEvidenceContext(null);
        setStatus(evidenceStatusLabel, "Loading incidents for " + attempt.studentName() + "...", null);

        AsyncRunner.runAsync(
                () -> apiClient.adminGetProctorEvents(attemptId),
                (List<AdminProctorEventDto> events) -> {
                    if (reqId != currentProctorEventsReqId) return;
                    setStatus(evidenceStatusLabel, events == null || events.isEmpty()
                            ? "No proctoring incidents recorded for this attempt."
                            : "Loaded " + events.size() + " incident(s).", events == null || events.isEmpty() ? null : "status-success");
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
                    setStatus(evidenceStatusLabel,
                            "Unable to load proctoring incidents. Check the server connection and retry.", "status-error");
                }
        );
    }

    private void clearEvidenceViewer() {
        evidenceImageView.setImage(null);
        currentEvidenceBytes = null;
        currentEvidenceEventId = -1;
        evidenceZoom = 1.0;
        evidenceFitMode = true;
        evidenceZoomLabel.setText("100%");
    }

    private void zoomEvidence(double factor) {
        if (evidenceImageView.getImage() == null) return;
        evidenceFitMode = false;
        evidenceZoom = Math.max(0.25, Math.min(4.0, evidenceZoom * factor));
        applyEvidenceZoom();
    }

    private void resetEvidenceZoom() {
        if (evidenceImageView.getImage() == null) return;
        evidenceFitMode = false;
        evidenceZoom = 1.0;
        applyEvidenceZoom();
    }

    private void fitEvidenceToWindow() {
        if (evidenceImageView.getImage() == null || evidenceScrollPane == null) return;
        evidenceFitMode = true;
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
        updateEvidenceContext(event);

        List<EvidenceChoice> choices = EvidenceChoice.forTypes(event.evidenceTypes());
        if (!evidenceTypeCombo.getItems().equals(choices)) {
            updatingEvidenceChoices = true;
            try {
                evidenceTypeCombo.getItems().setAll(choices);
                evidenceTypeCombo.getSelectionModel().selectFirst();
            } finally {
                updatingEvidenceChoices = false;
            }
        }
        EvidenceChoice choice = evidenceTypeCombo.getValue();
        String selectedType = choice == null ? null : choice.storageType();
        currentEvidenceType = selectedType;
        List<String> availableTypes = event.evidenceTypes() != null ? event.evidenceTypes() : List.of();

        if (selectedType == null || availableTypes.isEmpty()) {
            clearEvidenceViewer();
            Long dur = event.awayDurationMs();
            if (dur != null && dur < 800) {
                evidenceStatusLabel.setText("No screenshot captured — student returned before evidence delay (" + dur + "ms).");
            } else {
                evidenceStatusLabel.setText("No screenshot evidence attached to this incident.");
            }
            return;
        }

        String cacheKey = eventId + ":" + selectedType;
        byte[] cached = evidenceCache.get(cacheKey);
        if (cached != null) {
            displayEvidenceBytes(eventId, cached, selectedType);
            return;
        }

        evidenceStatusLabel.setText("Loading " + choice + "...");
        AsyncRunner.runAsync(
                () -> apiClient.adminGetEvidence(eventId, selectedType),
                (byte[] imageBytes) -> {
                    if (reqId != currentEvidenceReqId) return;
                    AdminProctorEventDto currentSel = proctorEventsTable.getSelectionModel().getSelectedItem();
                    if (currentSel == null || currentSel.id() != eventId) return;

                    if (imageBytes == null || imageBytes.length == 0) {
                        clearEvidenceViewer();
                        evidenceStatusLabel.setText("The attached evidence file is empty.");
                        return;
                    }

                    evidenceCache.put(cacheKey, imageBytes);
                    displayEvidenceBytes(eventId, imageBytes, selectedType);
                },
                (Throwable err) -> {
                    if (reqId != currentEvidenceReqId) return;
                    evidenceStatusLabel.setText("Failed to retrieve evidence: " + err.getMessage());
                }
        );
    }

    private void displayEvidenceBytes(int eventId, byte[] imageBytes, String evidenceType) {
        try {
            currentEvidenceBytes = imageBytes;
            currentEvidenceEventId = eventId;
            currentEvidenceType = evidenceType;
            Image img = new Image(new ByteArrayInputStream(imageBytes));
            evidenceImageView.setImage(img);
            evidenceFitMode = true;
            Platform.runLater(this::fitEvidenceToWindow);
            evidenceStatusLabel.setText("Screenshot evidence loaded (" + evidenceTypeCombo.getValue() + ").");
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
        viewerStage.setTitle("ExaSecure Evidence Inspector");
        AppIcon.applyTo(viewerStage);

        BorderPane modalRoot = new BorderPane();
        modalRoot.getStyleClass().add("app-root");

        HBox bar = new HBox(10);
        bar.setPadding(new Insets(10, 16, 10, 16));
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.getStyleClass().addAll("app-header", "app-header-admin");

        Label title = new Label(sel.eventType() + " (" + currentEvidenceType + ") evidence inspection");
        title.getStyleClass().add("header-title");

        Region barSpacer = new Region();
        HBox.setHgrow(barSpacer, Priority.ALWAYS);

        final double[] modalZoom = {1.0};
        Label modalZoomLbl = new Label("100%");
        modalZoomLbl.getStyleClass().add("header-meta");
        modalZoomLbl.setMinWidth(50);

        Image img = new Image(new ByteArrayInputStream(currentEvidenceBytes));
        ImageView modalImageView = new ImageView(img);
        modalImageView.setPreserveRatio(true);

        Runnable applyModalZoom = () -> {
            modalImageView.setFitWidth(img.getWidth() * modalZoom[0]);
            modalImageView.setFitHeight(img.getHeight() * modalZoom[0]);
            modalZoomLbl.setText(String.format(java.util.Locale.ROOT, "%.0f%%", modalZoom[0] * 100));
        };

        Button zIn = new Button("+ Zoom In");
        zIn.getStyleClass().add("btn-header");
        zIn.setOnAction(e -> { modalZoom[0] = Math.min(4.0, modalZoom[0] * 1.25); applyModalZoom.run(); });

        Button zOut = new Button("- Zoom Out");
        zOut.getStyleClass().add("btn-header");
        zOut.setOnAction(e -> { modalZoom[0] = Math.max(0.25, modalZoom[0] / 1.25); applyModalZoom.run(); });

        Button zReset = new Button("100%");
        zReset.getStyleClass().add("btn-header");
        zReset.setOnAction(e -> { modalZoom[0] = 1.0; applyModalZoom.run(); });

        Button fitBtn = new Button("Fit");
        fitBtn.getStyleClass().add("btn-header");

        Button closeBtn = new Button("Close");
        closeBtn.getStyleClass().add("btn-danger");
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
        modalScroll.getStyleClass().add("evidence-canvas");
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

    private void updateEvidenceContext(AdminProctorEventDto event) {
        AdminExamDto exam = proctorExamCombo.getValue();
        AdminAttemptDto attempt = proctorAttemptsTable.getSelectionModel().getSelectedItem();
        if (attempt == null) {
            evidenceContextLabel.setText("No attempt or incident selected.");
            return;
        }
        StringBuilder context = new StringBuilder();
        context.append("Student: ").append(attempt.studentName());
        if (attempt.enrollmentNumber() != null && !attempt.enrollmentNumber().isBlank()) {
            context.append("  •  Enrollment: ").append(attempt.enrollmentNumber());
        }
        if (exam != null) context.append("  •  Exam: ").append(exam.title());
        context.append("\nAttempt: ").append(attempt.startTime() != null ? attempt.startTime() : "Time unavailable")
                .append("  •  Violations: ").append(attempt.focusLostCount())
                .append("  •  ").append(attempt.flagged() ? "FLAGGED" : "CLEAN");
        if (event != null) {
            context.append("\nIncident #").append(event.id())
                    .append(" (").append(event.eventType()).append(")")
                    .append("  •  Left: ").append(event.recordedAt() != null ? event.recordedAt() : "-")
                    .append("  •  Returned: ").append(event.returnedAt() != null ? event.returnedAt() : "Ongoing / Not recorded");
            if (event.awayDurationMs() != null) {
                if (event.awayDurationMs() < 1000) {
                    context.append("  •  Duration: ").append(event.awayDurationMs()).append("ms");
                } else {
                    context.append("  •  Duration: ").append(String.format(java.util.Locale.ROOT, "%.1fs", event.awayDurationMs() / 1000.0));
                }
            }
            context.append("  •  Long Absence: ").append(event.isLongAbsence() ? "YES (≥5s)" : "NO");
        }
        evidenceContextLabel.setText(context.toString());
    }

    private static void setStatus(Label label, String message, String stateClass) {
        label.setText(message);
        label.getStyleClass().removeAll("status-error", "status-warning", "status-success");
        if (!label.getStyleClass().contains("status-text")) {
            label.getStyleClass().add("status-text");
        }
        if (stateClass != null) {
            label.getStyleClass().add(stateClass);
        }
    }

    private <T> TableCell<T, String> createBadgeCell() {
        return new TableCell<>() {
            private final Label badge = new Label();

            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                setText(null);
                if (empty || item == null) {
                    setGraphic(null);
                    return;
                }
                String normalized = item.toUpperCase();
                String display = normalized.contains("FLAGGED") ? "FLAGGED" : normalized;
                badge.setText(display);
                badge.getStyleClass().setAll(badgeClass(normalized));
                setGraphic(badge);
            }
        };
    }

    private String badgeClass(String value) {
        if (value.contains("FLAGGED")) return "badge-flagged";
        return switch (value) {
            case "ACTIVE" -> "badge-active";
            case "SUBMITTED" -> "badge-submitted";
            case "EXPIRED" -> "badge-expired";
            case "CLEAN" -> "badge-clean";
            case "INACTIVE", "ARCHIVED / INACTIVE" -> "badge-inactive";
            default -> "badge-neutral";
        };
    }
}
