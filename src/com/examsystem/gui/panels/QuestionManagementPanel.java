package com.examsystem.gui.panels;

import com.examsystem.gui.dialogs.EditQuestionDialog;
import com.examsystem.models.Exam;
import com.examsystem.models.Question;
import com.examsystem.services.AdminService;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.List;

public class QuestionManagementPanel extends JPanel {

    private final AdminService adminService;
    private final JComboBox<ExamItem> examCombo = new JComboBox<>();

    // "Add Question" form fields (basic for now, will be updated to dynamic in Phase 3)
    private final JTextArea newQuestionArea = new JTextArea(3, 40);
    private final JTextField[] newOptionFields = { new JTextField(20), new JTextField(20), new JTextField(20), new JTextField(20) };
    private final JComboBox<Integer> correctCombo = new JComboBox<>(new Integer[]{1,2,3,4});
    private final JComboBox<String> typeCombo = new JComboBox<>(new String[]{"SINGLE", "MULTI"});

    private final DefaultTableModel model = new DefaultTableModel(
            new Object[]{"ID", "Type", "Question Text", "Correct Option #"}, 0) {
        @Override public boolean isCellEditable(int row, int column) { return false; }
    };
    private final JTable table = new JTable(model);

    public QuestionManagementPanel(AdminService adminService) {
        this.adminService = adminService;
        setLayout(new BorderLayout(8, 8));

        // --- TOP: Select Exam & Add New Question ---
        JPanel topPanel = new JPanel(new BorderLayout(5, 5));
        
        JPanel examSelectPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        examSelectPanel.add(new JLabel("Select Exam:"));
        examSelectPanel.add(examCombo);
        JButton refreshExamsBtn = new JButton("Refresh Exams");
        examSelectPanel.add(refreshExamsBtn);
        
        topPanel.add(examSelectPanel, BorderLayout.NORTH);

        JPanel addForm = new JPanel(new GridBagLayout());
        addForm.setBorder(BorderFactory.createTitledBorder("Add New Question"));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(4, 4, 4, 4);
        gbc.anchor = GridBagConstraints.WEST;

        gbc.gridx = 0; gbc.gridy = 0; addForm.add(new JLabel("Text:"), gbc);
        gbc.gridx = 1; gbc.gridwidth = 3; addForm.add(new JScrollPane(newQuestionArea), gbc);
        
        gbc.gridwidth = 1;
        for (int i = 0; i < 4; i++) {
            gbc.gridy = 1 + (i / 2);
            gbc.gridx = (i % 2) * 2;
            addForm.add(new JLabel("Opt " + (i + 1) + ":"), gbc);
            gbc.gridx = (i % 2) * 2 + 1;
            addForm.add(newOptionFields[i], gbc);
        }

        gbc.gridy = 3; gbc.gridx = 0; addForm.add(new JLabel("Type:"), gbc);
        gbc.gridx = 1; addForm.add(typeCombo, gbc);

        gbc.gridy = 3; gbc.gridx = 2; addForm.add(new JLabel("Correct:"), gbc);
        gbc.gridx = 3; addForm.add(correctCombo, gbc);
        
        JButton addBtn = new JButton("Add Question");
        gbc.gridy = 4; gbc.gridx = 2; gbc.gridwidth = 2; gbc.anchor = GridBagConstraints.EAST;
        addForm.add(addBtn, gbc);

        topPanel.add(addForm, BorderLayout.CENTER);
        add(topPanel, BorderLayout.NORTH);

        // --- CENTER: Question Table ---
        add(new JScrollPane(table), BorderLayout.CENTER);

        // --- BOTTOM: Edit / Delete Actions ---
        JPanel bottomPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton editBtn = new JButton("Edit Selected Question");
        JButton deleteBtn = new JButton("Delete Selected Question");
        deleteBtn.setForeground(Color.RED);
        
        bottomPanel.add(editBtn);
        bottomPanel.add(deleteBtn);
        add(bottomPanel, BorderLayout.SOUTH);

        // --- Listeners ---
        refreshExamsBtn.addActionListener(e -> loadExams());
        examCombo.addActionListener(e -> loadQuestions());
        addBtn.addActionListener(e -> onAddQuestion());
        editBtn.addActionListener(e -> onEditQuestion());
        deleteBtn.addActionListener(e -> onDeleteQuestion());

        loadExams();
    }

    private void loadExams() {
        examCombo.removeAllItems();
        List<Exam> exams = adminService.listAllExams();
        for (Exam ex : exams) {
            examCombo.addItem(new ExamItem(ex.getId(), ex.getTitle(), ex.getDurationMinutes()));
        }
    }

    private void loadQuestions() {
        model.setRowCount(0);
        ExamItem selected = (ExamItem) examCombo.getSelectedItem();
        if (selected == null) return;

        List<Question> questions = adminService.getQuestionsForExam(selected.id);
        for (Question q : questions) {
            model.addRow(new Object[]{q.getId(), q.getQuestionType(), q.getQuestionText(), q.getCorrectOptionNumber()});
        }
    }

    private void onAddQuestion() {
        ExamItem selected = (ExamItem) examCombo.getSelectedItem();
        if (selected == null) {
            JOptionPane.showMessageDialog(this, "Please select an exam.", "Validation", JOptionPane.WARNING_MESSAGE);
            return;
        }
        
        String qText = newQuestionArea.getText().trim();
        if (qText.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Question text is required.", "Validation", JOptionPane.WARNING_MESSAGE);
            return;
        }

        java.util.List<String> validOpts = new java.util.ArrayList<>();
        for(JTextField field : newOptionFields) {
            String txt = field.getText().trim();
            if(!txt.isEmpty()) {
                validOpts.add(txt);
            }
        }

        if (validOpts.size() < 2) {
            JOptionPane.showMessageDialog(this, "Please enter at least 2 options.", "Validation", JOptionPane.WARNING_MESSAGE);
            return;
        }

        int correct = (Integer) correctCombo.getSelectedItem();
        if (correct > validOpts.size()) {
            JOptionPane.showMessageDialog(this, "Correct option exceeds number of entered options.", "Validation", JOptionPane.WARNING_MESSAGE);
            return;
        }

        String type = (String) typeCombo.getSelectedItem();

        boolean ok = adminService.addQuestionToExam(selected.id, qText, validOpts.toArray(new String[0]), correct, type);
        if (ok) {
            JOptionPane.showMessageDialog(this, "Question added.", "Success", JOptionPane.INFORMATION_MESSAGE);
            newQuestionArea.setText("");
            for (JTextField f : newOptionFields) f.setText("");
            correctCombo.setSelectedIndex(0);
            typeCombo.setSelectedIndex(0);
            loadQuestions();
        } else {
            JOptionPane.showMessageDialog(this, "Failed to add question.", "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void onEditQuestion() {
        int row = table.getSelectedRow();
        if (row < 0) {
            JOptionPane.showMessageDialog(this, "Select a question first.", "Validation", JOptionPane.WARNING_MESSAGE);
            return;
        }
        int qId = (Integer) model.getValueAt(row, 0);
        
        // We need to fetch the full question with options. The list in the table only has basic info.
        com.examsystem.dao.QuestionDAO qDao = new com.examsystem.dao.QuestionDAO();
        Question q = qDao.getById(qId);
        
        if (q != null) {
            EditQuestionDialog dialog = new EditQuestionDialog(SwingUtilities.getWindowAncestor(this), adminService, q);
            dialog.setVisible(true);
            if (dialog.isUpdated()) {
                loadQuestions();
            }
        } else {
            JOptionPane.showMessageDialog(this, "Failed to load question details.", "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void onDeleteQuestion() {
        int row = table.getSelectedRow();
        if (row < 0) {
            JOptionPane.showMessageDialog(this, "Select a question to delete.", "Validation", JOptionPane.WARNING_MESSAGE);
            return;
        }
        int qId = (Integer) model.getValueAt(row, 0);

        if (JOptionPane.showConfirmDialog(this, "Delete question ID " + qId + "?", "Confirm", JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION) {
            if (adminService.softDeleteQuestion(qId)) {
                JOptionPane.showMessageDialog(this, "Question deleted.", "Success", JOptionPane.INFORMATION_MESSAGE);
                loadQuestions();
            } else {
                JOptionPane.showMessageDialog(this, "Failed to delete question.", "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private static class ExamItem {
        final int id; final String title; final int duration;
        ExamItem(int id, String title, int duration) { this.id = id; this.title = title; this.duration = duration; }
        @Override public String toString() { return "[" + id + "] " + title; }
    }
}
