package com.examsystem.gui.panels;

import com.examsystem.models.User;
import com.examsystem.services.AdminService;

import javax.swing.*;
import java.awt.*;

/** Panel to create a new exam (title + duration). */
public class CreateExamPanel extends JPanel {

    private final JTextField titleField = new JTextField(30);
    private final JSpinner durationSpinner = new JSpinner(new SpinnerNumberModel(2, 1, 300, 1));

    private final User adminUser;
    private final AdminService adminService;

    public CreateExamPanel(User adminUser, AdminService adminService) {
        this.adminUser = adminUser;
        this.adminService = adminService;

        setLayout(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(8, 8, 8, 8);
        gbc.anchor = GridBagConstraints.WEST;

        int row = 0;

        gbc.gridx = 0; gbc.gridy = row;
        add(new JLabel("Exam Title:"), gbc);
        gbc.gridx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1;
        add(titleField, gbc);

        row++;
        gbc.gridx = 0; gbc.gridy = row;
        gbc.weightx = 0; gbc.fill = GridBagConstraints.NONE;
        add(new JLabel("Duration (minutes):"), gbc);
        gbc.gridx = 1;
        add(durationSpinner, gbc);

        row++;
        gbc.gridx = 0; gbc.gridy = row; gbc.gridwidth = 2;
        gbc.anchor = GridBagConstraints.CENTER;
        JButton createBtn = new JButton("Create Exam");
        add(createBtn, gbc);

        createBtn.addActionListener(e -> onCreate());
    }

    private void onCreate() {
        String title = titleField.getText().trim();
        int duration = (Integer) durationSpinner.getValue();

        if (title.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Title cannot be empty.", "Validation", JOptionPane.WARNING_MESSAGE);
            return;
        }

        int examId = adminService.createExam(title, duration, adminUser.getId());
        if (examId > 0) {
            JOptionPane.showMessageDialog(this, "Exam created with ID: " + examId, "Success", JOptionPane.INFORMATION_MESSAGE);
            titleField.setText("");
            durationSpinner.setValue(2);
        } else {
            JOptionPane.showMessageDialog(this, "Failed to create exam.", "Error", JOptionPane.ERROR_MESSAGE);
        }
    }
}