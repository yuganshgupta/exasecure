package com.examsystem.gui.dialogs;

import com.examsystem.models.Exam;
import com.examsystem.services.AdminService;

import javax.swing.*;
import java.awt.*;

public class EditExamDialog extends JDialog {

    private final AdminService adminService;
    private final Exam exam;
    private boolean updated = false;

    private final JTextField titleField = new JTextField(30);
    private final JSpinner durationSpinner = new JSpinner(new SpinnerNumberModel(2, 1, 300, 1));

    public EditExamDialog(Window owner, AdminService adminService, Exam exam) {
        super(owner, "Edit Exam", Dialog.ModalityType.APPLICATION_MODAL);
        this.adminService = adminService;
        this.exam = exam;

        setLayout(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(8, 8, 8, 8);
        gbc.anchor = GridBagConstraints.WEST;

        // Pre-fill data
        titleField.setText(exam.getTitle());
        durationSpinner.setValue(exam.getDurationMinutes());

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
        
        JPanel btnPanel = new JPanel(new FlowLayout());
        JButton saveBtn = new JButton("Save Changes");
        JButton cancelBtn = new JButton("Cancel");
        
        btnPanel.add(saveBtn);
        btnPanel.add(cancelBtn);
        add(btnPanel, gbc);

        saveBtn.addActionListener(e -> onSave());
        cancelBtn.addActionListener(e -> dispose());

        pack();
        setLocationRelativeTo(owner);
    }

    private void onSave() {
        String title = titleField.getText().trim();
        int duration = (Integer) durationSpinner.getValue();

        if (title.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Title cannot be empty.", "Validation", JOptionPane.WARNING_MESSAGE);
            return;
        }
        
        if (title.length() > 255) {
            JOptionPane.showMessageDialog(this, "Title is too long (max 255).", "Validation", JOptionPane.WARNING_MESSAGE);
            return;
        }

        if (adminService.updateExam(exam.getId(), title, duration)) {
            JOptionPane.showMessageDialog(this, "Exam updated successfully.", "Success", JOptionPane.INFORMATION_MESSAGE);
            updated = true;
            dispose();
        } else {
            JOptionPane.showMessageDialog(this, "Failed to update exam.", "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    public boolean isUpdated() {
        return updated;
    }
}
