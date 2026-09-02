package com.examsystem.client.admin.views;

import com.examsystem.client.admin.AdminApp;
import com.examsystem.client.api.ApiClient;
import com.examsystem.client.api.ApiException;
import com.examsystem.client.async.AsyncRunner;
import com.examsystem.client.ui.UiFeedback;
import com.examsystem.contracts.admin.*;
import javafx.beans.property.SimpleStringProperty;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class AdminDashboardView {
    private final AdminApp app;
    private final ApiClient apiClient;
    private final BorderPane view;
    private final TabPane tabPane;

    // Users Tab Controls
    private final TableView<AdminUserDto> usersTable;
    private final Label usersStatusLabel;

    // Exams Tab Controls
    private final TableView<AdminExamDto> examsTable;
    private final Label examsStatusLabel;

    // Attempts Tab Controls
    private final TextField attemptsExamIdField;
    private final TableView<AdminAttemptDto> attemptsTable;
    private final Label attemptsStatusLabel;

    // Proctoring Tab Controls
    private final TextField proctorAttemptIdField;
    private final TableView<AdminProctorEventDto> proctorEventsTable;
    private final ImageView evidenceImageView;
    private final Label evidenceStatusLabel;

    public AdminDashboardView(AdminApp app, ApiClient apiClient) {
        this.app = app;
        this.apiClient = apiClient;
        this.view = new BorderPane();
        this.tabPane = new TabPane();

        this.usersTable = new TableView<>();
        this.usersStatusLabel = new Label();

        this.examsTable = new TableView<>();
        this.examsStatusLabel = new Label();

        this.attemptsExamIdField = new TextField();
        this.attemptsTable = new TableView<>();
        this.attemptsStatusLabel = new Label();

        this.proctorAttemptIdField = new TextField();
        this.proctorEventsTable = new TableView<>();
        this.evidenceImageView = new ImageView();
        this.evidenceStatusLabel = new Label("Select a proctor event with evidence to inspect screenshot.");

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

        topBar.getChildren().addAll(brand, adminBadge, spacer, userLabel, logoutBtn);
        view.setTop(topBar);

        // Tab Navigation
        tabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        tabPane.getTabs().addAll(
                createUsersTab(),
                createExamsTab(),
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

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button addBtn = new Button("+ Add User");
        addBtn.setStyle("-fx-background-color: #2563eb; -fx-text-fill: white; -fx-font-weight: bold; -fx-background-radius: 6px; -fx-padding: 7 14; -fx-cursor: hand;");
        addBtn.setOnAction(e -> showCreateUserDialog());

        Button editBtn = new Button("Edit User");
        editBtn.setStyle("-fx-background-color: white; -fx-border-color: #cbd5e1; -fx-border-radius: 6px; -fx-padding: 7 14; -fx-cursor: hand;");
        editBtn.setOnAction(e -> {
            AdminUserDto selected = usersTable.getSelectionModel().getSelectedItem();
            if (selected == null) {
                UiFeedback.showError("Selection Required", "Please select a user to edit.");
                return;
            }
            showEditUserDialog(selected);
        });

        Button deactivateBtn = new Button("Deactivate");
        deactivateBtn.setStyle("-fx-background-color: #fee2e2; -fx-text-fill: #b91c1c; -fx-font-weight: bold; -fx-background-radius: 6px; -fx-padding: 7 14; -fx-cursor: hand;");
        deactivateBtn.setOnAction(e -> {
            AdminUserDto selected = usersTable.getSelectionModel().getSelectedItem();
            if (selected == null) {
                UiFeedback.showError("Selection Required", "Please select a user to deactivate.");
                return;
            }
            confirmDeactivateUser(selected);
        });

        Button refreshBtn = new Button("Refresh");
        refreshBtn.setStyle("-fx-background-color: white; -fx-border-color: #cbd5e1; -fx-border-radius: 6px; -fx-padding: 7 14; -fx-cursor: hand;");
        refreshBtn.setOnAction(e -> loadUsers());

        toolbar.getChildren().addAll(title, spacer, addBtn, editBtn, deactivateBtn, refreshBtn);

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
        idCol.setMaxWidth(70);

        TableColumn<AdminUserDto, String> usernameCol = new TableColumn<>("Username");
        usernameCol.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().username()));

        TableColumn<AdminUserDto, String> nameCol = new TableColumn<>("Full Name");
        nameCol.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().fullName()));

        TableColumn<AdminUserDto, String> roleCol = new TableColumn<>("Role");
        roleCol.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().role()));

        TableColumn<AdminUserDto, String> activeCol = new TableColumn<>("Active");
        activeCol.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().isActive() ? "Active" : "Inactive"));

        usersTable.getColumns().addAll(idCol, usernameCol, nameCol, roleCol, activeCol);
    }

    private void loadUsers() {
        usersTable.getItems().clear();
        usersStatusLabel.setText("Loading user accounts...");
        AsyncRunner.runAsync(
                apiClient::adminListUsers,
                (List<AdminUserDto> users) -> {
                    usersStatusLabel.setText("");
                    if (users != null) usersTable.getItems().addAll(users);
                },
                (Throwable err) -> usersStatusLabel.setText("Failed to load users: " + err.getMessage())
        );
    }

    private void showCreateUserDialog() {
        Dialog<AdminCreateUserRequestDto> dialog = new Dialog<>();
        dialog.setTitle("Create New User Account");
        dialog.setHeaderText("Enter details for the new user account:");

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
                UiFeedback.showError("Validation Error", "Username, password, and full name are required.");
                return;
            }
            AsyncRunner.runAsync(
                    () -> apiClient.adminCreateUser(req),
                    (Integer id) -> {
                        UiFeedback.showInfo("User Created", "User created successfully with ID: " + id);
                        loadUsers();
                    },
                    (Throwable err) -> UiFeedback.showError("Creation Failed", err.getMessage())
            );
        });
    }

    private void showEditUserDialog(AdminUserDto user) {
        Dialog<AdminUpdateUserRequestDto> dialog = new Dialog<>();
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
        TextField enrollment = new TextField();
        TextField section = new TextField();
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
        title.setStyle("-fx-text-fill: #0f172a;");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button createBtn = new Button("+ Create New Exam");
        createBtn.setStyle("-fx-background-color: #2563eb; -fx-text-fill: white; -fx-font-weight: bold; -fx-background-radius: 6px; -fx-padding: 7 14; -fx-cursor: hand;");
        createBtn.setOnAction(e -> showCreateExamDialog());

        Button reviseBtn = new Button("Revise Selected Exam");
        reviseBtn.setStyle("-fx-background-color: white; -fx-border-color: #cbd5e1; -fx-border-radius: 6px; -fx-padding: 7 14; -fx-cursor: hand;");
        reviseBtn.setOnAction(e -> {
            AdminExamDto selected = examsTable.getSelectionModel().getSelectedItem();
            if (selected == null) {
                UiFeedback.showError("Selection Required", "Please select an exam to revise.");
                return;
            }
            showReviseExamDialog(selected);
        });

        Button deactivateBtn = new Button("Deactivate");
        deactivateBtn.setStyle("-fx-background-color: #fee2e2; -fx-text-fill: #b91c1c; -fx-font-weight: bold; -fx-background-radius: 6px; -fx-padding: 7 14; -fx-cursor: hand;");
        deactivateBtn.setOnAction(e -> {
            AdminExamDto selected = examsTable.getSelectionModel().getSelectedItem();
            if (selected == null) {
                UiFeedback.showError("Selection Required", "Please select an exam to deactivate.");
                return;
            }
            confirmDeactivateExam(selected);
        });

        Button refreshBtn = new Button("Refresh");
        refreshBtn.setStyle("-fx-background-color: white; -fx-border-color: #cbd5e1; -fx-border-radius: 6px; -fx-padding: 7 14; -fx-cursor: hand;");
        refreshBtn.setOnAction(e -> loadExams());

        toolbar.getChildren().addAll(title, spacer, createBtn, reviseBtn, deactivateBtn, refreshBtn);

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

        TableColumn<AdminExamDto, String> supersedesCol = new TableColumn<>("Supersedes ID");
        supersedesCol.setCellValueFactory(d -> new SimpleStringProperty(
                d.getValue().supersedesExamId() != null ? "#" + d.getValue().supersedesExamId() : "-"));

        examsTable.getColumns().addAll(idCol, titleCol, durCol, verCol, activeCol, supersedesCol);
    }

    private void loadExams() {
        examsTable.getItems().clear();
        examsStatusLabel.setText("Loading examinations from server...");
        AsyncRunner.runAsync(
                apiClient::adminListExams,
                (List<AdminExamDto> exams) -> {
                    examsStatusLabel.setText("");
                    if (exams != null) examsTable.getItems().addAll(exams);
                },
                (Throwable err) -> examsStatusLabel.setText("Failed to load exams: " + err.getMessage())
        );
    }

    private void showCreateExamDialog() {
        ExamDefinitionBuilderDialog builder = new ExamDefinitionBuilderDialog(
                "Create New Examination",
                "Define exam metadata and questions:",
                null,
                null
        );
        Optional<AdminExamDefinitionRequestDto> result = builder.showAndWait();
        result.ifPresent(req -> {
            AsyncRunner.runAsync(
                    () -> apiClient.adminCreateExam(req),
                    (AdminExamMutationResultDto res) -> {
                        UiFeedback.showInfo("Exam Created", "Exam created with ID #" + res.id() + " (Version " + res.versionNumber() + ").");
                        loadExams();
                    },
                    (Throwable err) -> UiFeedback.showError("Creation Failed", err.getMessage())
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
                    Optional<AdminExamDefinitionRequestDto> result = builder.showAndWait();
                    result.ifPresent(req -> {
                        AsyncRunner.runAsync(
                                () -> apiClient.adminReviseExam(exam.id(), req),
                                (AdminExamMutationResultDto res) -> {
                                    UiFeedback.showInfo("Exam Revised", "New revision created with ID #" + res.id() + " (Version " + res.versionNumber() + ").");
                                    loadExams();
                                },
                                (Throwable err) -> UiFeedback.showError("Revision Failed", err.getMessage())
                        );
                    });
                },
                (Throwable err) -> {
                    examsStatusLabel.setText("");
                    UiFeedback.showError("Failed to Load Questions", err.getMessage());
                }
        );
    }

    private void confirmDeactivateExam(AdminExamDto exam) {
        boolean confirmed = UiFeedback.showConfirmation(
                "Deactivate Exam",
                "Deactivate exam #" + exam.id() + " (" + exam.title() + ")?",
                "Students will no longer be able to start new attempts for this exam."
        );
        if (confirmed) {
            AsyncRunner.runAsync(
                    () -> {
                        apiClient.adminDeactivateExam(exam.id());
                        return true;
                    },
                    (Boolean ok) -> {
                        UiFeedback.showInfo("Exam Deactivated", "Exam #" + exam.id() + " has been deactivated.");
                        loadExams();
                    },
                    (Throwable err) -> UiFeedback.showError("Deactivation Failed", err.getMessage())
            );
        }
    }

    // ==========================================
    // 3. Attempts & Results Tab
    // ==========================================

    private Tab createAttemptsTab() {
        Tab tab = new Tab("Attempts & Results");
        VBox content = new VBox(14);
        content.setPadding(new Insets(20));

        HBox filterBar = new HBox(12);
        filterBar.setAlignment(Pos.CENTER_LEFT);

        Label label = new Label("Exam ID:");
        label.setStyle("-fx-font-weight: bold;");

        attemptsExamIdField.setPromptText("Enter Exam ID (e.g. 1)");
        attemptsExamIdField.setPrefWidth(160);

        Button loadBtn = new Button("Load Attempts");
        loadBtn.setStyle("-fx-background-color: #2563eb; -fx-text-fill: white; -fx-font-weight: bold; -fx-background-radius: 6px; -fx-padding: 6 14; -fx-cursor: hand;");
        loadBtn.setOnAction(e -> loadAttempts());

        Button inspectProctorBtn = new Button("Inspect Proctor Events for Selected");
        inspectProctorBtn.setStyle("-fx-background-color: white; -fx-border-color: #cbd5e1; -fx-border-radius: 6px; -fx-padding: 6 14; -fx-cursor: hand;");
        inspectProctorBtn.setOnAction(e -> {
            AdminAttemptDto sel = attemptsTable.getSelectionModel().getSelectedItem();
            if (sel == null) {
                UiFeedback.showError("Selection Required", "Please select an attempt to inspect.");
                return;
            }
            proctorAttemptIdField.setText(String.valueOf(sel.id()));
            tabPane.getSelectionModel().select(3); // Switch to Proctoring tab
            loadProctorEvents();
        });

        filterBar.getChildren().addAll(label, attemptsExamIdField, loadBtn, inspectProctorBtn);

        setupAttemptsTable();

        content.getChildren().addAll(filterBar, attemptsStatusLabel, attemptsTable);
        VBox.setVgrow(attemptsTable, Priority.ALWAYS);
        tab.setContent(content);
        return tab;
    }

    private void setupAttemptsTable() {
        attemptsTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        attemptsTable.setPlaceholder(new Label("Enter an Exam ID above to view student attempts."));

        TableColumn<AdminAttemptDto, String> idCol = new TableColumn<>("Attempt ID");
        idCol.setCellValueFactory(d -> new SimpleStringProperty("#" + d.getValue().id()));
        idCol.setMaxWidth(90);

        TableColumn<AdminAttemptDto, String> stuIdCol = new TableColumn<>("Student ID");
        stuIdCol.setCellValueFactory(d -> new SimpleStringProperty(String.valueOf(d.getValue().studentId())));
        stuIdCol.setMaxWidth(90);

        TableColumn<AdminAttemptDto, String> nameCol = new TableColumn<>("Student Name");
        nameCol.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().studentName()));

        TableColumn<AdminAttemptDto, String> startCol = new TableColumn<>("Start Time");
        startCol.setCellValueFactory(d -> new SimpleStringProperty(
                d.getValue().startTime() != null ? d.getValue().startTime() : "-"));

        TableColumn<AdminAttemptDto, String> statusCol = new TableColumn<>("Status");
        statusCol.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().status()));

        TableColumn<AdminAttemptDto, String> scoreCol = new TableColumn<>("Score");
        scoreCol.setCellValueFactory(d -> new SimpleStringProperty(
                d.getValue().score() != null ? d.getValue().score() + " pts" : "Pending"));

        attemptsTable.getColumns().addAll(idCol, stuIdCol, nameCol, startCol, statusCol, scoreCol);
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

        attemptsTable.getItems().clear();
        attemptsStatusLabel.setText("Loading attempts for exam #" + examId + "...");
        AsyncRunner.runAsync(
                () -> apiClient.adminGetAttemptHistory(examId),
                (List<AdminAttemptDto> attempts) -> {
                    attemptsStatusLabel.setText("");
                    if (attempts != null) attemptsTable.getItems().addAll(attempts);
                },
                (Throwable err) -> attemptsStatusLabel.setText("Failed to load attempts: " + err.getMessage())
        );
    }

    // ==========================================
    // 4. Proctoring Review Tab
    // ==========================================

    private Tab createProctoringTab() {
        Tab tab = new Tab("Proctoring & Evidence");
        VBox content = new VBox(14);
        content.setPadding(new Insets(20));

        HBox filterBar = new HBox(12);
        filterBar.setAlignment(Pos.CENTER_LEFT);

        Label label = new Label("Attempt ID:");
        label.setStyle("-fx-font-weight: bold;");

        proctorAttemptIdField.setPromptText("Enter Attempt ID (e.g. 201)");
        proctorAttemptIdField.setPrefWidth(160);

        Button loadBtn = new Button("Load Proctor Events");
        loadBtn.setStyle("-fx-background-color: #2563eb; -fx-text-fill: white; -fx-font-weight: bold; -fx-background-radius: 6px; -fx-padding: 6 14; -fx-cursor: hand;");
        loadBtn.setOnAction(e -> loadProctorEvents());

        filterBar.getChildren().addAll(label, proctorAttemptIdField, loadBtn);

        // Split view: Left Table, Right Image Viewer
        SplitPane splitPane = new SplitPane();

        VBox left = new VBox(8);
        setupProctorEventsTable();
        left.getChildren().addAll(new Label("Recorded Proctor Events:"), proctorEventsTable);
        VBox.setVgrow(proctorEventsTable, Priority.ALWAYS);

        VBox right = new VBox(10);
        right.setPadding(new Insets(10));
        right.setAlignment(Pos.TOP_CENTER);
        right.setStyle("-fx-background-color: white; -fx-border-color: #e2e8f0; -fx-border-radius: 6px;");

        Label evidenceHeader = new Label("Screenshot Evidence Viewer");
        evidenceHeader.setFont(Font.font("Segoe UI", FontWeight.BOLD, 15));

        evidenceImageView.setFitWidth(480);
        evidenceImageView.setFitHeight(320);
        evidenceImageView.setPreserveRatio(true);
        evidenceImageView.setStyle("-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.1), 8, 0, 0, 2);");

        ScrollPane imgScroll = new ScrollPane(evidenceImageView);
        imgScroll.setFitToWidth(true);
        imgScroll.setStyle("-fx-background: transparent; -fx-background-color: transparent;");

        right.getChildren().addAll(evidenceHeader, evidenceStatusLabel, imgScroll);

        splitPane.getItems().addAll(left, right);
        splitPane.setDividerPositions(0.45);

        content.getChildren().addAll(filterBar, splitPane);
        VBox.setVgrow(splitPane, Priority.ALWAYS);
        tab.setContent(content);
        return tab;
    }

    private void setupProctorEventsTable() {
        proctorEventsTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        proctorEventsTable.setPlaceholder(new Label("Enter an Attempt ID to review proctor events."));

        TableColumn<AdminProctorEventDto, String> idCol = new TableColumn<>("Event ID");
        idCol.setCellValueFactory(d -> new SimpleStringProperty("#" + d.getValue().id()));
        idCol.setMaxWidth(80);

        TableColumn<AdminProctorEventDto, String> typeCol = new TableColumn<>("Event Type");
        typeCol.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().eventType()));

        TableColumn<AdminProctorEventDto, String> timeCol = new TableColumn<>("Timestamp");
        timeCol.setCellValueFactory(d -> new SimpleStringProperty(
                d.getValue().recordedAt() != null ? d.getValue().recordedAt() : "-"));

        TableColumn<AdminProctorEventDto, String> evidenceCol = new TableColumn<>("Evidence");
        evidenceCol.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().hasEvidence() ? "✓ Image Attached" : "None"));

        proctorEventsTable.getColumns().addAll(idCol, typeCol, timeCol, evidenceCol);

        proctorEventsTable.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                loadEvidenceImage(newVal);
            }
        });
    }

    private void loadProctorEvents() {
        String input = proctorAttemptIdField.getText().trim();
        if (input.isEmpty()) return;
        int attemptId;
        try {
            attemptId = Integer.parseInt(input);
        } catch (NumberFormatException e) {
            UiFeedback.showError("Invalid Input", "Attempt ID must be an integer.");
            return;
        }

        proctorEventsTable.getItems().clear();
        evidenceImageView.setImage(null);
        evidenceStatusLabel.setText("Loading proctor events for attempt #" + attemptId + "...");

        AsyncRunner.runAsync(
                () -> apiClient.adminGetProctorEvents(attemptId),
                (List<AdminProctorEventDto> events) -> {
                    evidenceStatusLabel.setText("Loaded " + (events != null ? events.size() : 0) + " events.");
                    if (events != null) proctorEventsTable.getItems().addAll(events);
                },
                (Throwable err) -> evidenceStatusLabel.setText("Failed to load events: " + err.getMessage())
        );
    }

    private void loadEvidenceImage(AdminProctorEventDto event) {
        if (!event.hasEvidence()) {
            evidenceImageView.setImage(null);
            evidenceStatusLabel.setText("Event #" + event.id() + " (" + event.eventType() + ") has no screenshot evidence.");
            return;
        }

        evidenceStatusLabel.setText("Fetching authenticated screenshot bytes for Event #" + event.id() + "...");
        AsyncRunner.runAsync(
                () -> apiClient.adminGetEvidence(event.id()),
                (byte[] imageBytes) -> {
                    if (imageBytes == null || imageBytes.length == 0) {
                        evidenceImageView.setImage(null);
                        evidenceStatusLabel.setText("Evidence file is empty.");
                        return;
                    }
                    try {
                        Image img = new Image(new ByteArrayInputStream(imageBytes));
                        evidenceImageView.setImage(img);
                        evidenceStatusLabel.setText("Event #" + event.id() + " evidence loaded (" + imageBytes.length + " bytes).");
                    } catch (Exception e) {
                        evidenceStatusLabel.setText("Failed to decode screenshot image: " + e.getMessage());
                    }
                },
                (Throwable err) -> evidenceStatusLabel.setText("Failed to retrieve screenshot: " + err.getMessage())
        );
    }
}
