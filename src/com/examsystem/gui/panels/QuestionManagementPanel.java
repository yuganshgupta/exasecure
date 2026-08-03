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
    private final JToggleButton[] correctToggles = new JToggleButton[4];
    private final JPanel optionsContainer = new JPanel(new GridBagLayout());
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
        
        gbc.gridwidth = 4;
        gbc.gridy = 1; gbc.gridx = 0;
        addForm.add(optionsContainer, gbc);
        buildOptionsUI(); // Initialize toggles based on typeCombo

        gbc.gridwidth = 1;
        gbc.gridy = 2; gbc.gridx = 0; addForm.add(new JLabel("Type:"), gbc);
        gbc.gridx = 1; addForm.add(typeCombo, gbc);
        
        JButton addBtn = new JButton("Add Question");
        gbc.gridy = 3; gbc.gridx = 2; gbc.gridwidth = 2; gbc.anchor = GridBagConstraints.EAST;
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
        
        typeCombo.addActionListener(e -> buildOptionsUI());

        loadExams();
    }

    private void loadExams() {
        examCombo.removeAllItems();
        List<Exam> exams = adminService.listAllExams();
        for (Exam ex : exams) {
            examCombo.addItem(new ExamItem(ex.getId(), ex.getTitle(), ex.getDurationMinutes()));
        }
    }

    private void buildOptionsUI() {
        optionsContainer.removeAll();
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(4, 4, 4, 4);
        gbc.anchor = GridBagConstraints.WEST;

        boolean isMulti = "MULTI".equals(typeCombo.getSelectedItem());
        ButtonGroup group = new ButtonGroup();

        for (int i = 0; i < 4; i++) {
            gbc.gridy = i / 2;
            gbc.gridx = (i % 2) * 3;
            
            // Re-create toggle to ensure proper type
            boolean wasSelected = (correctToggles[i] != null && correctToggles[i].isSelected());
            if (i == 0 && correctToggles[i] == null) wasSelected = true; // default first option to correct
            
            correctToggles[i] = isMulti ? new JCheckBox() : new JRadioButton();
            correctToggles[i].setSelected(wasSelected);
            if (!isMulti) group.add(correctToggles[i]);

            optionsContainer.add(correctToggles[i], gbc);
            
            gbc.gridx = (i % 2) * 3 + 1;
            optionsContainer.add(new JLabel("Opt " + (i + 1) + ":"), gbc);
            
            gbc.gridx = (i % 2) * 3 + 2;
            optionsContainer.add(newOptionFields[i], gbc);
        }

        optionsContainer.revalidate();
        optionsContainer.repaint();
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

        boolean[] isCorrectArray = new boolean[validOpts.size()];
        int correctCount = 0;
        for (int i = 0; i < validOpts.size(); i++) {
            if (correctToggles[i].isSelected()) {
                isCorrectArray[i] = true;
                correctCount++;
            }
        }

        String type = (String) typeCombo.getSelectedItem();
        
        if ("SINGLE".equals(type) && correctCount != 1) {
            JOptionPane.showMessageDialog(this, "SINGLE questions must have exactly one correct option.", "Validation", JOptionPane.WARNING_MESSAGE);
            return;
        } else if ("MULTI".equals(type) && correctCount < 1) {
            JOptionPane.showMessageDialog(this, "MULTI questions must have at least one correct option.", "Validation", JOptionPane.WARNING_MESSAGE);
            return;
        }

        boolean ok = adminService.addQuestionToExam(selected.id, qText, validOpts.toArray(new String[0]), isCorrectArray, type);
        if (ok) {
            JOptionPane.showMessageDialog(this, "Question added.", "Success", JOptionPane.INFORMATION_MESSAGE);
            newQuestionArea.setText("");
            for (JTextField f : newOptionFields) f.setText("");
            typeCombo.setSelectedIndex(0);
            buildOptionsUI();
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
