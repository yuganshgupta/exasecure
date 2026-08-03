package com.examsystem.gui.panels;

import com.examsystem.gui.dialogs.EditExamDialog;
import com.examsystem.models.Exam;
import com.examsystem.models.User;
import com.examsystem.services.AdminService;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.List;

public class ExamManagementPanel extends JPanel {

    private final AdminService adminService;
    private final User adminUser;

    private final JTextField titleField = new JTextField(30);
    private final JSpinner durationSpinner = new JSpinner(new SpinnerNumberModel(2, 1, 300, 1));

    private final DefaultTableModel model = new DefaultTableModel(
            new Object[]{"ID", "Title", "Duration (mins)", "Created At"}, 0) {
        @Override public boolean isCellEditable(int row, int column) { return false; }
    };
    private final JTable table = new JTable(model);

    public ExamManagementPanel(User adminUser, AdminService adminService) {
        this.adminUser = adminUser;
        this.adminService = adminService;

        setLayout(new BorderLayout(8, 8));

        // Top form for creating exams
        JPanel form = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(8, 8, 8, 8);
        gbc.anchor = GridBagConstraints.WEST;

        int row = 0;

        gbc.gridx = 0; gbc.gridy = row;
        form.add(new JLabel("Exam Title:"), gbc);
        gbc.gridx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1;
        form.add(titleField, gbc);

        row++;
        gbc.gridx = 0; gbc.gridy = row;
        gbc.weightx = 0; gbc.fill = GridBagConstraints.NONE;
        form.add(new JLabel("Duration (minutes):"), gbc);
        gbc.gridx = 1;
        form.add(durationSpinner, gbc);

        row++;
        gbc.gridx = 0; gbc.gridy = row; gbc.gridwidth = 2; gbc.anchor = GridBagConstraints.CENTER;
        JButton createBtn = new JButton("Create Exam");
        form.add(createBtn, gbc);

        JPanel top = new JPanel(new BorderLayout());
        top.add(form, BorderLayout.CENTER);

        // Buttons above table
        JPanel btns = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton refreshBtn = new JButton("Refresh Exams");
        btns.add(refreshBtn);
        top.add(btns, BorderLayout.SOUTH);

        add(top, BorderLayout.NORTH);
        add(new JScrollPane(table), BorderLayout.CENTER);

        // Bottom panel for edit/delete
        JPanel bottomPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton editBtn = new JButton("Edit Selected Exam");
        JButton deleteBtn = new JButton("Delete Selected Exam");
        deleteBtn.setForeground(Color.RED);
        bottomPanel.add(editBtn);
        bottomPanel.add(deleteBtn);
        
        add(bottomPanel, BorderLayout.SOUTH);

        // Listeners
        createBtn.addActionListener(e -> onCreate());
        refreshBtn.addActionListener(e -> loadExams());
        editBtn.addActionListener(e -> onEdit());
        deleteBtn.addActionListener(e -> onDelete());

        loadExams();
    }

    private void onCreate() {
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

        int examId = adminService.createExam(title, duration, adminUser.getId());
        if (examId > 0) {
            JOptionPane.showMessageDialog(this, "Exam created with ID: " + examId, "Success", JOptionPane.INFORMATION_MESSAGE);
            titleField.setText("");
            durationSpinner.setValue(2);
            loadExams();
        } else {
            JOptionPane.showMessageDialog(this, "Failed to create exam.", "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void onEdit() {
        int row = table.getSelectedRow();
        if (row < 0) {
            JOptionPane.showMessageDialog(this, "Select an exam first.", "Validation", JOptionPane.WARNING_MESSAGE);
            return;
        }
        
        int examId = (Integer) model.getValueAt(row, 0);
        Exam exam = adminService.getExamById(examId);
        
        if (exam != null) {
            EditExamDialog dialog = new EditExamDialog(SwingUtilities.getWindowAncestor(this), adminService, exam);
            dialog.setVisible(true);
            if (dialog.isUpdated()) {
                loadExams();
            }
        } else {
            JOptionPane.showMessageDialog(this, "Failed to load exam details.", "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void onDelete() {
        int row = table.getSelectedRow();
        if (row < 0) {
            JOptionPane.showMessageDialog(this, "Select an exam to delete.");
            return;
        }
        int examId = (Integer) model.getValueAt(row, 0);
        String title = (String) model.getValueAt(row, 1);

        if (JOptionPane.showConfirmDialog(this, "Delete exam '" + title + "'?", "Confirm Deletion", JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION) {
            if (adminService.softDeleteExam(examId)) {
                JOptionPane.showMessageDialog(this, "Exam deleted.", "Success", JOptionPane.INFORMATION_MESSAGE);
                loadExams();
            } else {
                JOptionPane.showMessageDialog(this, "Failed to delete exam.", "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void loadExams() {
        List<Exam> exams = adminService.listAllExams();
        model.setRowCount(0);
        for (Exam ex : exams) {
            model.addRow(new Object[]{
                ex.getId(), 
                ex.getTitle(), 
                ex.getDurationMinutes(),
                ex.getCreatedAt()
            });
        }
    }
}
