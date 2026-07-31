package com.examsystem.gui.panels;

import com.examsystem.models.Exam;
import com.examsystem.services.AdminService;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/** Panel to add questions (allows blank options). */
public class AddQuestionPanel extends JPanel {

    private final AdminService adminService;

    private final JComboBox<ExamItem> examCombo = new JComboBox<>();
    private final JTextArea questionArea = new JTextArea(4, 40);
    // Still showing 4 boxes, but users can leave some blank
    private final JTextField[] optionFields = { new JTextField(30), new JTextField(30), new JTextField(30), new JTextField(30) };
    private final JComboBox<Integer> correctCombo = new JComboBox<>(new Integer[]{1,2,3,4});

    public AddQuestionPanel(AdminService adminService) {
        this.adminService = adminService;

        setLayout(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(8, 8, 8, 8);
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;

        int row = 0;

        gbc.gridx = 0; gbc.gridy = row; gbc.gridwidth = 1;
        add(new JLabel("Select Exam:"), gbc);
        gbc.gridx = 1; gbc.weightx = 1;
        add(examCombo, gbc);
        gbc.gridx = 2; gbc.weightx = 0;
        JButton refreshBtn = new JButton("Refresh Exams");
        add(refreshBtn, gbc);

        row++;
        gbc.gridx = 0; gbc.gridy = row;
        add(new JLabel("Question Text:"), gbc);
        gbc.gridx = 1; gbc.gridwidth = 2;
        add(new JScrollPane(questionArea), gbc);

        row++;
        gbc.gridwidth = 1;
        for (int i = 0; i < 4; i++) {
            gbc.gridx = 0; gbc.gridy = ++row;
            add(new JLabel("Option " + (i + 1) + ":"), gbc);
            gbc.gridx = 1; gbc.gridwidth = 2;
            add(optionFields[i], gbc);
            gbc.gridwidth = 1;
        }

        row++;
        gbc.gridx = 0; gbc.gridy = row;
        add(new JLabel("Correct Option (of valid entries):"), gbc);
        gbc.gridx = 1;
        add(correctCombo, gbc);

        row++;
        gbc.gridx = 0; gbc.gridy = row; gbc.gridwidth = 3; gbc.anchor = GridBagConstraints.CENTER;
        JButton addBtn = new JButton("Add Question");
        add(addBtn, gbc);

        refreshBtn.addActionListener(e -> loadExams());
        addBtn.addActionListener(e -> onAddQuestion());

        loadExams();
    }

    private void loadExams() {
        examCombo.removeAllItems();
        List<Exam> exams = adminService.listAllExams();
        for (Exam ex : exams) {
            examCombo.addItem(new ExamItem(ex.getId(), ex.getTitle(), ex.getDurationMinutes()));
        }
    }

    private void onAddQuestion() {
        ExamItem selected = (ExamItem) examCombo.getSelectedItem();
        if (selected == null) {
            JOptionPane.showMessageDialog(this, "Please select an exam.", "Validation", JOptionPane.WARNING_MESSAGE);
            return;
        }
        String qText = questionArea.getText().trim();
        if (qText.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Question text is required.", "Validation", JOptionPane.WARNING_MESSAGE);
            return;
        }
        
        if (qText.length() > 65535) {
            JOptionPane.showMessageDialog(this, "Question text is too long.", "Validation", JOptionPane.WARNING_MESSAGE);
            return;
        }

        // Collect only non-empty options
        List<String> validOpts = new ArrayList<>();
        for(JTextField field : optionFields) {
            String txt = field.getText().trim();
            if(!txt.isEmpty()) {
                if (txt.length() > 500) {
                    JOptionPane.showMessageDialog(this, "Option text too long (max 500).", "Validation", JOptionPane.WARNING_MESSAGE);
                    return;
                }
                validOpts.add(txt);
            }
        }

        if (validOpts.size() < 2) {
            JOptionPane.showMessageDialog(this, "Please enter at least 2 options.", "Validation", JOptionPane.WARNING_MESSAGE);
            return;
        }

        int correct = (Integer) correctCombo.getSelectedItem();
        if (correct > validOpts.size()) {
            JOptionPane.showMessageDialog(this, "Correct option cannot be " + correct + " (you only entered " + validOpts.size() + " options).", "Validation", JOptionPane.WARNING_MESSAGE);
            return;
        }

        // Convert list back to array for service
        String[] optionsArr = validOpts.toArray(new String[0]);

        boolean ok = adminService.addQuestionToExam(selected.id, qText, optionsArr, correct);
        if (ok) {
            JOptionPane.showMessageDialog(this, "Question added.", "Success", JOptionPane.INFORMATION_MESSAGE);
            questionArea.setText("");
            for (JTextField f : optionFields) f.setText("");
            correctCombo.setSelectedIndex(0);
        } else {
            JOptionPane.showMessageDialog(this, "Failed to add question.", "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private static class ExamItem {
        final int id; final String title; final int duration;
        ExamItem(int id, String title, int duration) { this.id = id; this.title = title; this.duration = duration; }
        @Override public String toString() { return "[" + id + "] " + title + " (" + duration + " min)"; }
    }
}