package com.examsystem.dao;

import com.examsystem.db.DatabaseConnector;
import com.examsystem.models.Option;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/** DAO for options. */
public class OptionDAO {

    public void addOption(int questionId, int optionNumber, String optionText, boolean isCorrect) {
        String sql = "INSERT INTO options (question_id, option_number, option_text, is_correct) VALUES (?, ?, ?, ?)";
        try (Connection conn = DatabaseConnector.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, questionId);
            ps.setInt(2, optionNumber);
            ps.setString(3, optionText);
            ps.setBoolean(4, isCorrect);
            ps.executeUpdate();

        } catch (SQLException e) {
            System.err.println("OptionDAO.addOption error: " + e.getMessage());
        }
    }

    public List<Option> getOptionsByQuestionId(int questionId) {
        String sql = "SELECT id, question_id, option_number, option_text, is_correct FROM options WHERE question_id=? AND is_active=TRUE ORDER BY option_number ASC";
        List<Option> list = new ArrayList<>();
        try (Connection conn = DatabaseConnector.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, questionId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(new Option(
                            rs.getInt("id"),
                            rs.getInt("question_id"),
                            rs.getInt("option_number"),
                            rs.getString("option_text"),
                            rs.getBoolean("is_correct")
                    ));
                }
            }
        } catch (SQLException e) {
            System.err.println("OptionDAO.getOptionsByQuestionId error: " + e.getMessage());
        }
        return list;
    }

    public boolean softDeleteOption(int optionId) {
        String sql = "UPDATE options SET is_active = FALSE WHERE id = ?";
        try (Connection conn = DatabaseConnector.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, optionId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("OptionDAO.softDeleteOption error: " + e.getMessage());
        }
        return false;
    }

    public boolean updateOption(Option option) {
        String sql = "UPDATE options SET option_text = ?, is_correct = ? WHERE id = ?";
        try (Connection conn = DatabaseConnector.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, option.getOptionText());
            ps.setBoolean(2, option.isCorrect());
            ps.setInt(3, option.getId());
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("OptionDAO.updateOption error: " + e.getMessage());
        }
        return false;
    }
}