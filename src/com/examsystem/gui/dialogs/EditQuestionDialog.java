package com.examsystem.gui.dialogs;

import com.examsystem.models.Option;
import com.examsystem.models.Question;
import com.examsystem.services.AdminService;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class EditQuestionDialog extends JDialog {

    private final AdminService adminService;
    private final Question question;
    private boolean updated = false;

    private final JTextArea questionArea = new JTextArea(4, 40);
    private final JPanel optionsPanel = new JPanel();
    private final ButtonGroup correctGroup = new ButtonGroup();
    
    private final List<OptionRow> optionRows = new ArrayList<>();

    public EditQuestionDialog(Window owner, AdminService adminService, Question question) {
        super(owner, "Edit Question", Dialog.ModalityType.APPLICATION_MODAL);
        this.adminService = adminService;
        this.question = question;

        setLayout(new BorderLayout(8, 8));

        // Top: Question Text
        JPanel topPanel = new JPanel(new BorderLayout(5, 5));
        topPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 5, 10));
        topPanel.add(new JLabel("Question Text:"), BorderLayout.NORTH);
        questionArea.setText(question.getQuestionText());
        topPanel.add(new JScrollPane(questionArea), BorderLayout.CENTER);
        
        add(topPanel, BorderLayout.NORTH);

        // Center: Dynamic Options
        optionsPanel.setLayout(new BoxLayout(optionsPanel, BoxLayout.Y_AXIS));
        optionsPanel.setBorder(BorderFactory.createEmptyBorder(5, 10, 5, 10));
        
        for (Option opt : question.getOptions()) {
            addOptionRow(opt);
        }

        JScrollPane optionsScroll = new JScrollPane(optionsPanel);
        optionsScroll.setPreferredSize(new Dimension(500, 200));
        add(optionsScroll, BorderLayout.CENTER);

        // Bottom: Action Buttons
        JPanel bottomPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton addOptionBtn = new JButton("Add Option");
        JButton saveBtn = new JButton("Save Changes");
        JButton cancelBtn = new JButton("Cancel");

        bottomPanel.add(addOptionBtn);
        bottomPanel.add(saveBtn);
        bottomPanel.add(cancelBtn);
        add(bottomPanel, BorderLayout.SOUTH);

        addOptionBtn.addActionListener(e -> {
            addOptionRow(new Option(0, question.getId(), optionRows.size() + 1, "", false));
            optionsPanel.revalidate();
            optionsScroll.getVerticalScrollBar().setValue(optionsScroll.getVerticalScrollBar().getMaximum());
        });

        saveBtn.addActionListener(e -> onSave());
        cancelBtn.addActionListener(e -> dispose());

        pack();
        setLocationRelativeTo(owner);
    }

    private void addOptionRow(Option opt) {
        OptionRow row = new OptionRow(opt);
        optionRows.add(row);
        correctGroup.add(row.radio);
        optionsPanel.add(row.panel);
    }

    private void onSave() {
        String qText = questionArea.getText().trim();
        if (qText.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Question text cannot be empty.", "Validation", JOptionPane.WARNING_MESSAGE);
            return;
        }

        // Validate options
        int correctCount = 0;
        int activeCount = 0;
        int newCorrectOptionNumber = 1;

        for (int i = 0; i < optionRows.size(); i++) {
            OptionRow row = optionRows.get(i);
            if (!row.isDeleted) {
                activeCount++;
                if (row.field.getText().trim().isEmpty()) {
                    JOptionPane.showMessageDialog(this, "Option text cannot be empty.", "Validation", JOptionPane.WARNING_MESSAGE);
                    return;
                }
                if (row.radio.isSelected()) {
                    correctCount++;
                    newCorrectOptionNumber = i + 1;
                }
            }
        }

        if (activeCount < 2) {
            JOptionPane.showMessageDialog(this, "Must have at least 2 active options.", "Validation", JOptionPane.WARNING_MESSAGE);
            return;
        }
        if (correctCount != 1) {
            JOptionPane.showMessageDialog(this, "Must select exactly one correct option.", "Validation", JOptionPane.WARNING_MESSAGE);
            return;
        }

        // Update Question
        question.setQuestionText(qText);
        question.setCorrectOptionNumber(newCorrectOptionNumber);
        
        if (!adminService.updateQuestion(question)) {
            JOptionPane.showMessageDialog(this, "Failed to update question.", "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        // Update/Delete Options
        for (int i = 0; i < optionRows.size(); i++) {
            OptionRow row = optionRows.get(i);
            if (row.isDeleted) {
                if (row.option.getId() > 0) {
                    adminService.softDeleteOption(row.option.getId());
                }
            } else {
                row.option.setOptionText(row.field.getText().trim());
                row.option.setCorrect(row.radio.isSelected());
                // We keep the original optionNumber to maintain history order if desired, or we can rewrite them.
                // For now, just update text and is_correct.
                if (row.option.getId() > 0) {
                    adminService.updateOption(row.option);
                } else {
                    // New option added dynamically
                    // The DAO only has addOption(qId, optNum, text, isCorrect) currently.
                    // Let's assume AdminService will expose addOption if needed, or we just rely on existing.
                    // Wait, AdminService doesn't have a direct addOption. Let's use OptionDAO if we have to, 
                    // but since AdminService isn't exposing it, I should update AdminService or just not support adding new options in edit yet.
                    // The prompt asked for dynamic option handling. We'll leave it as a TODO or just use OptionDAO directly for this demo.
                    com.examsystem.dao.OptionDAO oDao = new com.examsystem.dao.OptionDAO();
                    oDao.addOption(question.getId(), i + 1, row.option.getOptionText(), row.option.isCorrect());
                }
            }
        }

        updated = true;
        JOptionPane.showMessageDialog(this, "Question updated successfully.", "Success", JOptionPane.INFORMATION_MESSAGE);
        dispose();
    }

    public boolean isUpdated() {
        return updated;
    }

    private class OptionRow {
        Option option;
        JPanel panel;
        JTextField field;
        JRadioButton radio;
        JButton deleteBtn;
        boolean isDeleted = false;

        OptionRow(Option opt) {
            this.option = opt;
            panel = new JPanel(new BorderLayout(5, 5));
            panel.setBorder(BorderFactory.createEmptyBorder(2, 0, 2, 0));
            
            radio = new JRadioButton();
            radio.setSelected(opt.isCorrect());
            
            field = new JTextField(opt.getOptionText(), 30);
            
            deleteBtn = new JButton("X");
            deleteBtn.setForeground(Color.RED);
            deleteBtn.setMargin(new Insets(2, 5, 2, 5));
            
            deleteBtn.addActionListener(e -> {
                isDeleted = true;
                panel.setVisible(false);
                if (radio.isSelected()) {
                    correctGroup.clearSelection();
                }
            });

            JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
            left.add(radio);
            left.add(new JLabel(" Opt: "));
            
            panel.add(left, BorderLayout.WEST);
            panel.add(field, BorderLayout.CENTER);
            panel.add(deleteBtn, BorderLayout.EAST);
        }
    }
}
