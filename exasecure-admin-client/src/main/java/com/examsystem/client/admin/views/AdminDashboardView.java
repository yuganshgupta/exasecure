package com.examsystem.client.admin.views;

import com.examsystem.client.admin.AdminApp;
import com.examsystem.client.api.ApiClient;
import com.examsystem.client.api.ApiException;
import com.examsystem.client.async.AsyncRunner;
import com.examsystem.client.ui.UiFeedback;
import com.examsystem.contracts.AdminAttemptDetailDto;
import com.examsystem.contracts.AdminExamStatisticsDto;
import com.examsystem.contracts.admin.*;
import javafx.beans.property.SimpleStringProperty;
import javafx.geometry.Insets;
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
import java.util.List;
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
    private final TextField proctorAttemptIdField = new TextField();
    private final TableView<AdminProctorEventDto> proctorEventsTable = new TableView<>();
    private final ImageView evidenceImageView = new ImageView();
    private final Label evidenceStatusLabel = new Label("Select a proctor event with evidence to inspect screenshot.");
    private byte[] currentEvidenceBytes = null;

    public AdminDashboardView(AdminApp app, ApiClient apiClient) {
        this.app = app;
        this.apiClient = apiClient;
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

        topBar.getChildren().addAll(brand, adminBadge, spacer, userLabel, logoutBtn);

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

        toolbar.getChildren().addAll(title, userSearchField, spacer, addBtn, editBtn, deactivateBtn, refreshBtn);

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
                    }
                },
                (Throwable err) -> examsStatusLabel.setText("Failed to load exams: " + err.getMessage())
        );
    }

    private void updateExamCombos() {
        questionBankExamCombo.getItems().setAll(allExamsList);
        if (!allExamsList.isEmpty() && questionBankExamCombo.getValue() == null) {
            questionBankExamCombo.setValue(allExamsList.get(0));
        }

        attemptsExamCombo.getItems().setAll(allExamsList);
        if (!allExamsList.isEmpty() && attemptsExamCombo.getValue() == null) {
            attemptsExamCombo.setValue(allExamsList.get(0));
        }
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
        right.setStyle("-fx-background-color: white; -fx-border-color: #e2e8f0; -fx-border-radius: 8px;");

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
        qbQuestionsTable.getItems().clear();
        qbOptionsDetailsBox.getChildren().clear();
        qbStatusLabel.setText("Loading question bank for exam #" + examId + "...");

        AsyncRunner.runAsync(
                () -> apiClient.adminGetExamQuestions(examId),
                (List<AdminQuestionDefinitionDto> questions) -> {
                    qbStatusLabel.setText("");
                    if (questions != null) {
                        qbQuestionsTable.getItems().setAll(questions);
                        if (!questions.isEmpty()) {
                            qbQuestionsTable.getSelectionModel().select(0);
                        }
                    }
                },
                (Throwable err) -> qbStatusLabel.setText("Failed to load questions: " + err.getMessage())
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
        label.setStyle("-fx-font-weight: bold; -fx-text-fill: #1e293b;");

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
        loadBtn.setStyle("-fx-background-color: #2563eb; -fx-text-fill: white; -fx-font-weight: bold; -fx-background-radius: 6px; -fx-padding: 6 12; -fx-cursor: hand;");
        loadBtn.setOnAction(e -> loadAttempts());

        Region filterSpacer = new Region();
        HBox.setHgrow(filterSpacer, Priority.ALWAYS);

        Button exportCsvBtn = new Button("📥 Export to CSV");
        exportCsvBtn.setStyle("-fx-background-color: white; -fx-border-color: #10b981; -fx-text-fill: #059669; -fx-font-weight: bold; -fx-background-radius: 6px; -fx-border-radius: 6px; -fx-padding: 6 12; -fx-cursor: hand;");
        exportCsvBtn.setOnAction(e -> exportAttemptsToCsv());

        Button viewDetailsBtn = new Button("📋 View Report Card");
        viewDetailsBtn.setStyle("-fx-background-color: white; -fx-border-color: #cbd5e1; -fx-border-radius: 6px; -fx-padding: 6 12; -fx-cursor: hand;");
        viewDetailsBtn.setOnAction(e -> {
            AdminAttemptDto sel = attemptsTable.getSelectionModel().getSelectedItem();
            if (sel == null) {
                UiFeedback.showError("Selection Required", "Please select an attempt from the table to view the question breakdown.");
                return;
            }
            openAttemptReportCard(sel);
        });

        Button statsBtn = new Button("📊 Exam Analytics");
        statsBtn.setStyle("-fx-background-color: white; -fx-border-color: #cbd5e1; -fx-border-radius: 6px; -fx-padding: 6 12; -fx-cursor: hand;");
        statsBtn.setOnAction(e -> openExamStatistics());

        Button inspectProctorBtn = new Button("🛡️ Inspect Proctor Events");
        inspectProctorBtn.setStyle("-fx-background-color: white; -fx-border-color: #cbd5e1; -fx-border-radius: 6px; -fx-padding: 6 12; -fx-cursor: hand;");
        inspectProctorBtn.setOnAction(e -> {
            AdminAttemptDto sel = attemptsTable.getSelectionModel().getSelectedItem();
            if (sel == null) {
                UiFeedback.showError("Selection Required", "Please select an attempt to inspect proctoring events.");
                return;
            }
            proctorAttemptIdField.setText(String.valueOf(sel.id()));
            tabPane.getSelectionModel().select(4); // Switch to Proctoring tab
            loadProctorEvents();
        });

        filterBar.getChildren().addAll(label, attemptsExamCombo, attemptsExamIdField, loadBtn, filterSpacer, exportCsvBtn, viewDetailsBtn, statsBtn, inspectProctorBtn);

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

        TableColumn<AdminAttemptDto, String> startCol = new TableColumn<>("Start Time");
        startCol.setCellValueFactory(d -> new SimpleStringProperty(
                d.getValue().startTime() != null ? d.getValue().startTime() : "-"));

        TableColumn<AdminAttemptDto, String> statusCol = new TableColumn<>("Status");
        statusCol.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().status()));

        TableColumn<AdminAttemptDto, String> scoreCol = new TableColumn<>("Score");
        scoreCol.setCellValueFactory(d -> new SimpleStringProperty(
                d.getValue().score() != null ? d.getValue().score() + " pts" : "Pending"));

        attemptsTable.getColumns().addAll(idCol, stuIdCol, nameCol, startCol, statusCol, scoreCol);

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
                    if (attempts != null) {
                        attemptsTable.getItems().addAll(attempts);
                        metricAttemptsLabel.setText("Attempts: " + attempts.size() + " for exam #" + examId);
                    }
                },
                (Throwable err) -> attemptsStatusLabel.setText("Failed to load attempts: " + err.getMessage())
        );
    }

    private void openAttemptReportCard(AdminAttemptDto attempt) {
        attemptsStatusLabel.setText("Loading report card for attempt #" + attempt.id() + "...");
        AsyncRunner.runAsync(
                () -> apiClient.adminGetAttemptDetails(attempt.id()),
                (List<AdminAttemptDetailDto> details) -> {
                    attemptsStatusLabel.setText("");
                    AttemptDetailsDialog dialog = new AttemptDetailsDialog(attempt, details);
                    dialog.showAndWait();
                },
                (Throwable err) -> {
                    attemptsStatusLabel.setText("");
                    UiFeedback.showError("Failed to Load Details", err.getMessage());
                }
        );
    }

    private void openExamStatistics() {
        String input = attemptsExamIdField.getText().trim();
        if (input.isEmpty()) {
            UiFeedback.showError("Selection Required", "Please select an exam first to view statistics.");
            return;
        }
        int examId;
        try {
            examId = Integer.parseInt(input);
        } catch (NumberFormatException e) {
            UiFeedback.showError("Invalid Input", "Exam ID must be an integer.");
            return;
        }

        attemptsStatusLabel.setText("Loading analytics for exam #" + examId + "...");
        AsyncRunner.runAsync(
                () -> apiClient.adminGetExamStatistics(examId),
                (AdminExamStatisticsDto stats) -> {
                    attemptsStatusLabel.setText("");
                    ExamStatisticsDialog dialog = new ExamStatisticsDialog(stats);
                    dialog.showAndWait();
                },
                (Throwable err) -> {
                    attemptsStatusLabel.setText("");
                    UiFeedback.showError("Failed to Load Statistics", err.getMessage());
                }
        );
    }

    private void exportAttemptsToCsv() {
        List<AdminAttemptDto> items = attemptsTable.getItems();
        if (items.isEmpty()) {
            UiFeedback.showError("Nothing to Export", "There are no attempts loaded in the table to export.");
            return;
        }

        String examId = attemptsExamIdField.getText().trim();
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Export Attempts to CSV");
        fileChooser.setInitialFileName("attempts-exam-" + (examId.isEmpty() ? "all" : examId) + ".csv");
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV Files (*.csv)", "*.csv"));

        File file = fileChooser.showSaveDialog(view.getScene() != null ? view.getScene().getWindow() : null);
        if (file == null) return;

        try (FileWriter writer = new FileWriter(file)) {
            writer.write("Attempt ID,Student ID,Student Name,Start Time,Status,Score\n");
            for (AdminAttemptDto a : items) {
                writer.write(String.format("%d,%d,\"%s\",\"%s\",\"%s\",%s\n",
                        a.id(),
                        a.studentId(),
                        a.studentName().replace("\"", "\"\""),
                        a.startTime() != null ? a.startTime() : "",
                        a.status(),
                        a.score() != null ? String.valueOf(a.score()) : ""));
            }
            UiFeedback.showInfo("Export Complete", "Successfully exported " + items.size() + " attempts to:\n" + file.getAbsolutePath());
        } catch (IOException e) {
            UiFeedback.showError("Export Failed", "Error saving CSV file: " + e.getMessage());
        }
    }

    // ==========================================
    // 5. Proctoring Review Tab
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

        Region navSpacer = new Region();
        HBox.setHgrow(navSpacer, Priority.ALWAYS);

        Button prevEvBtn = new Button("← Prev Evidence");
        prevEvBtn.setStyle("-fx-background-color: white; -fx-border-color: #cbd5e1; -fx-border-radius: 6px; -fx-padding: 6 12; -fx-cursor: hand;");
        prevEvBtn.setOnAction(e -> navigateEvidence(-1));

        Button nextEvBtn = new Button("Next Evidence →");
        nextEvBtn.setStyle("-fx-background-color: white; -fx-border-color: #cbd5e1; -fx-border-radius: 6px; -fx-padding: 6 12; -fx-cursor: hand;");
        nextEvBtn.setOnAction(e -> navigateEvidence(1));

        Button enlargeBtn = new Button("🔍 Enlarge Image");
        enlargeBtn.setStyle("-fx-background-color: white; -fx-border-color: #cbd5e1; -fx-border-radius: 6px; -fx-padding: 6 12; -fx-cursor: hand;");
        enlargeBtn.setOnAction(e -> showEnlargedEvidence());

        filterBar.getChildren().addAll(label, proctorAttemptIdField, loadBtn, navSpacer, prevEvBtn, nextEvBtn, enlargeBtn);

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
        currentEvidenceBytes = null;
        evidenceStatusLabel.setText("Loading proctor events for attempt #" + attemptId + "...");

        AsyncRunner.runAsync(
                () -> apiClient.adminGetProctorEvents(attemptId),
                (List<AdminProctorEventDto> events) -> {
                    evidenceStatusLabel.setText("Loaded " + (events != null ? events.size() : 0) + " events.");
                    if (events != null) {
                        proctorEventsTable.getItems().addAll(events);
                        // Auto-select first event with evidence
                        for (AdminProctorEventDto ev : events) {
                            if (ev.hasEvidence()) {
                                proctorEventsTable.getSelectionModel().select(ev);
                                break;
                            }
                        }
                    }
                },
                (Throwable err) -> evidenceStatusLabel.setText("Failed to load events: " + err.getMessage())
        );
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
        UiFeedback.showInfo("Navigation", direction > 0 ? "No subsequent evidence events." : "No prior evidence events.");
    }

    private void loadEvidenceImage(AdminProctorEventDto event) {
        if (!event.hasEvidence()) {
            evidenceImageView.setImage(null);
            currentEvidenceBytes = null;
            evidenceStatusLabel.setText("Event #" + event.id() + " (" + event.eventType() + ") has no screenshot evidence.");
            return;
        }

        evidenceStatusLabel.setText("Fetching authenticated screenshot bytes for Event #" + event.id() + "...");
        AsyncRunner.runAsync(
                () -> apiClient.adminGetEvidence(event.id()),
                (byte[] imageBytes) -> {
                    if (imageBytes == null || imageBytes.length == 0) {
                        evidenceImageView.setImage(null);
                        currentEvidenceBytes = null;
                        evidenceStatusLabel.setText("Evidence file is empty.");
                        return;
                    }
                    try {
                        currentEvidenceBytes = imageBytes;
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

    private void showEnlargedEvidence() {
        if (currentEvidenceBytes == null || currentEvidenceBytes.length == 0) {
            UiFeedback.showError("No Evidence", "Please select an event with screenshot evidence to enlarge.");
            return;
        }

        Stage dialogStage = new Stage();
        dialogStage.initModality(Modality.NONE);
        dialogStage.setTitle("Evidence Screenshot - Full View");

        ImageView largeView = new ImageView(new Image(new ByteArrayInputStream(currentEvidenceBytes)));
        largeView.setPreserveRatio(true);
        largeView.setFitWidth(960);
        largeView.setFitHeight(640);

        ScrollPane scroll = new ScrollPane(largeView);
        scroll.setFitToWidth(true);
        scroll.setFitToHeight(true);

        Scene scene = new Scene(scroll, 1000, 700);
        dialogStage.setScene(scene);
        dialogStage.show();
    }
}
