package com.examsystem.server.adapters.jdbc;

import com.examsystem.application.commands.AdminExamDefinitionCommand;
import com.examsystem.application.commands.AdminOptionDefinitionCommand;
import com.examsystem.application.commands.AdminQuestionDefinitionCommand;
import com.examsystem.application.ports.AdminExamDefinitionMutationPort;
import com.examsystem.application.query.AdminExamMutationResult;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Optional;

public class JdbcAdminExamDefinitionMutationPort implements AdminExamDefinitionMutationPort {
    private final DataSource dataSource;

    public JdbcAdminExamDefinitionMutationPort(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public AdminExamMutationResult createDefinition(AdminExamDefinitionCommand definition, int createdBy) {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                AdminExamMutationResult result = insertDefinition(
                        connection, definition, createdBy, 1, null);
                connection.commit();
                return result;
            } catch (Exception exception) {
                connection.rollback();
                throw translate(exception);
            }
        } catch (SQLException exception) {
            throw translate(exception);
        }
    }

    @Override
    public Optional<AdminExamMutationResult> reviseDefinition(
            int supersededExamId, AdminExamDefinitionCommand definition, int createdBy) {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                Integer currentVersion = lockVersion(connection, supersededExamId);
                if (currentVersion == null) {
                    connection.rollback();
                    return Optional.empty();
                }
                if (hasSuccessor(connection, supersededExamId)) {
                    throw new IllegalStateException("This exam definition already has a successor");
                }
                AdminExamMutationResult result = insertDefinition(
                        connection, definition, createdBy, currentVersion + 1, supersededExamId);
                try (PreparedStatement deactivate = connection.prepareStatement(
                        "UPDATE exams SET is_active = 0 WHERE id = ?")) {
                    deactivate.setInt(1, supersededExamId);
                    deactivate.executeUpdate();
                }
                connection.commit();
                return Optional.of(result);
            } catch (Exception exception) {
                connection.rollback();
                throw translate(exception);
            }
        } catch (SQLException exception) {
            throw translate(exception);
        }
    }

    @Override
    public boolean deactivateDefinition(int examId) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "UPDATE exams SET is_active = 0 WHERE id = ? AND is_active = 1")) {
            statement.setInt(1, examId);
            return statement.executeUpdate() > 0;
        } catch (SQLException exception) {
            throw translate(exception);
        }
    }

    private static AdminExamMutationResult insertDefinition(
            Connection connection,
            AdminExamDefinitionCommand definition,
            int createdBy,
            int versionNumber,
            Integer supersedesExamId) throws SQLException {
        int examId;
        try (PreparedStatement exam = connection.prepareStatement(
                "INSERT INTO exams "
                        + "(title, duration_minutes, created_by, is_active, version_number, supersedes_exam_id) "
                        + "VALUES (?, ?, ?, 1, ?, ?)",
                Statement.RETURN_GENERATED_KEYS)) {
            exam.setString(1, definition.title());
            exam.setInt(2, definition.durationMinutes());
            exam.setInt(3, createdBy);
            exam.setInt(4, versionNumber);
            if (supersedesExamId == null) {
                exam.setNull(5, java.sql.Types.INTEGER);
            } else {
                exam.setInt(5, supersedesExamId);
            }
            exam.executeUpdate();
            try (ResultSet keys = exam.getGeneratedKeys()) {
                if (!keys.next()) {
                    throw new IllegalStateException("Exam was created without an ID");
                }
                examId = keys.getInt(1);
            }
        }

        for (AdminQuestionDefinitionCommand question : definition.questions()) {
            int questionId = insertQuestion(connection, examId, question);
            insertOptions(connection, questionId, question.options());
        }
        return new AdminExamMutationResult(examId, versionNumber);
    }

    private static int insertQuestion(
            Connection connection, int examId, AdminQuestionDefinitionCommand question) throws SQLException {
        int firstCorrect = 0;
        for (int index = 0; index < question.options().size(); index++) {
            if (question.options().get(index).correct()) {
                firstCorrect = index + 1;
                break;
            }
        }
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO questions "
                        + "(exam_id, question_text, correct_option_number, question_type, is_active) "
                        + "VALUES (?, ?, ?, ?, 1)",
                Statement.RETURN_GENERATED_KEYS)) {
            statement.setInt(1, examId);
            statement.setString(2, question.text());
            statement.setInt(3, firstCorrect);
            statement.setString(4, question.type());
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (keys.next()) {
                    return keys.getInt(1);
                }
            }
        }
        throw new IllegalStateException("Question was created without an ID");
    }

    private static void insertOptions(
            Connection connection, int questionId, java.util.List<AdminOptionDefinitionCommand> options)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO options "
                        + "(question_id, option_number, option_text, is_correct, is_active) "
                        + "VALUES (?, ?, ?, ?, 1)")) {
            for (int index = 0; index < options.size(); index++) {
                AdminOptionDefinitionCommand option = options.get(index);
                statement.setInt(1, questionId);
                statement.setInt(2, index + 1);
                statement.setString(3, option.text());
                statement.setBoolean(4, option.correct());
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    private static Integer lockVersion(Connection connection, int examId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT version_number FROM exams WHERE id = ? FOR UPDATE")) {
            statement.setInt(1, examId);
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? result.getInt(1) : null;
            }
        }
    }

    private static boolean hasSuccessor(Connection connection, int examId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT 1 FROM exams WHERE supersedes_exam_id = ?")) {
            statement.setInt(1, examId);
            try (ResultSet result = statement.executeQuery()) {
                return result.next();
            }
        }
    }

    private static RuntimeException translate(Exception exception) {
        if (exception instanceof RuntimeException runtimeException) {
            return runtimeException;
        }
        if (exception instanceof SQLException sqlException
                && sqlException.getSQLState() != null
                && sqlException.getSQLState().startsWith("23")) {
            return new IllegalStateException("Exam revision conflicts with existing data", exception);
        }
        return new RuntimeException("Database error", exception);
    }
}
