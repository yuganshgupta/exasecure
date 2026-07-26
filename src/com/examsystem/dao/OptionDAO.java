package com.examsystem.dao;

import com.examsystem.db.DatabaseConnector;
import com.examsystem.models.Option;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/** DAO for options. */
public class OptionDAO {

    public void addOption(int questionId, int optionNumber, String optionText) {
        String sql = "INSERT INTO options (question_id, option_number, option_text) VALUES (?, ?, ?)";
        try (Connection conn = DatabaseConnector.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, questionId);
            ps.setInt(2, optionNumber);
            ps.setString(3, optionText);
            ps.executeUpdate();

        } catch (SQLException e) {
            System.err.println("OptionDAO.addOption error: " + e.getMessage());
        }
    }

    public List<Option> getOptionsByQuestionId(int questionId) {
        String sql = "SELECT id, question_id, option_number, option_text FROM options WHERE question_id=? ORDER BY option_number ASC";
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
                            rs.getString("option_text")
                    ));
                }
            }
        } catch (SQLException e) {
            System.err.println("OptionDAO.getOptionsByQuestionId error: " + e.getMessage());
        }
        return list;
    }
}