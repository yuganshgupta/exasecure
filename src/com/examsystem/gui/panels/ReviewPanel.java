package com.examsystem.gui.panels;

import com.examsystem.gui.dialogs.AttemptDetailsDialog;
import com.examsystem.gui.dialogs.ProctorDialog;
import com.examsystem.gui.dialogs.StatisticsDialog;
import com.examsystem.gui.dialogs.ScreenshotViewer; // Import the new dialog
import com.examsystem.models.AttemptSummary;
import com.examsystem.models.Exam;
import com.examsystem.models.StudentAnswer;
import com.examsystem.services.AdminService;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class ReviewPanel extends JPanel {

    private final AdminService adminService;

    private final JComboBox<ExamItem> examCombo = new JComboBox<>();
    private final JComboBox<String> dateFilterCombo = new JComboBox<>();
    private final JComboBox<String> sectionFilterCombo = new JComboBox<>(new String[]{"All Sections", "A", "B"});
    
    private final DefaultTableModel summaryModel = new DefaultTableModel(
            new Object[]{"ID", "Enrollment", "Name", "Section", "Score", "Focus Lost", "Start", "End"}, 0) {
        @Override public boolean isCellEditable(int row, int column) { return false; }
    };
    private final JTable summaryTable = new JTable(summaryModel);

    private List<AttemptSummary> allSummaries = new ArrayList<>();

    public ReviewPanel(AdminService adminService) {
        this.adminService = adminService;

        setLayout(new BorderLayout(8, 8));

        // Top Bar
        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton refreshExamsBtn = new JButton("Refresh Exams");
        JButton loadBtn = new JButton("Load Data");
        
        top.add(new JLabel("Exam:"));
        top.add(examCombo);
        top.add(refreshExamsBtn);
        top.add(loadBtn);
        
        // Filter Bar
        top.add(new JSeparator(SwingConstants.VERTICAL));
        top.add(new JLabel("Filter Date:"));
        top.add(dateFilterCombo);
        top.add(new JLabel("Section:"));
        top.add(sectionFilterCombo);

        // Action Bar (Bottom)
        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        
        JButton statsBtn = new JButton("Analysis Charts");
        // --- NEW BUTTON ---
        JButton evidenceBtn = new JButton("View Screenshots");
        // ------------------
        JButton deleteBtn = new JButton("Delete Result");
        deleteBtn.setForeground(Color.RED);
        
        bottom.add(statsBtn);
        bottom.add(evidenceBtn); // Add to panel
        bottom.add(deleteBtn);

        add(top, BorderLayout.NORTH);
        add(new JScrollPane(summaryTable), BorderLayout.CENTER);
        add(bottom, BorderLayout.SOUTH);

        // Listeners
        refreshExamsBtn.addActionListener(e -> loadExams());
        loadBtn.addActionListener(e -> loadSummariesAndDates());
        dateFilterCombo.addActionListener(e -> applyFilters());
        sectionFilterCombo.addActionListener(e -> applyFilters());
        
        deleteBtn.addActionListener(e -> onDelete());
        statsBtn.addActionListener(e -> onShowStats());
        evidenceBtn.addActionListener(e -> onViewScreenshots()); // Link button

        // Double Click Listener
        summaryTable.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    onViewDetails();
                }
            }
        });

        loadExams();
    }

    private void onViewDetails() {
        int row = summaryTable.getSelectedRow();
        if (row < 0) return;
        
        int attemptId = (Integer) summaryTable.getValueAt(row, 0);
        
        AttemptSummary selected = allSummaries.stream()
                .filter(s -> s.getAttemptId() == attemptId)
                .findFirst().orElse(null);
        
        if (selected != null) {
            new AttemptDetailsDialog(SwingUtilities.getWindowAncestor(this), adminService, selected).setVisible(true);
        }
    }

    private void onShowStats() {
        if (allSummaries.isEmpty()) {
            JOptionPane.showMessageDialog(this, "No data to analyze.");
            return;
        }
        
        ExamItem item = (ExamItem) examCombo.getSelectedItem();
        if (item == null) return;

        String dateFilter = (String) dateFilterCombo.getSelectedItem();
        String sectionFilter = (String) sectionFilterCombo.getSelectedItem();

        new StatisticsDialog(
            SwingUtilities.getWindowAncestor(this), 
            adminService, 
            item.id, 
            dateFilter, 
            sectionFilter, 
            allSummaries 
        ).setVisible(true);
    }

    private void onViewScreenshots() {
        int row = summaryTable.getSelectedRow();
        if (row < 0) {
            JOptionPane.showMessageDialog(this, "Select a result first.", "Validation", JOptionPane.WARNING_MESSAGE);
            return;
        }
        
        int attemptId = (Integer) summaryTable.getValueAt(row, 0);
        
        // Open the new Screenshot Viewer
        new ScreenshotViewer(SwingUtilities.getWindowAncestor(this), adminService, attemptId).setVisible(true);
    }

    private void loadExams() {
        examCombo.removeAllItems();
        for (Exam ex : adminService.listAllExams()) {
            examCombo.addItem(new ExamItem(ex.getId(), ex.getTitle()));
        }
    }

    private void loadSummariesAndDates() {
        ExamItem item = (ExamItem) examCombo.getSelectedItem();
        if (item == null) {
            JOptionPane.showMessageDialog(this, "Select an exam first.", "Validation", JOptionPane.WARNING_MESSAGE);
            return;
        }
        
        allSummaries = adminService.getExamAttemptSummaries(item.id);
        List<String> dates = adminService.getDistinctDatesForExam(item.id);
        
        dateFilterCombo.removeAllItems();
        dateFilterCombo.addItem("All Dates");
        for (String d : dates) dateFilterCombo.addItem(d);
        
        sectionFilterCombo.setSelectedIndex(0);
        updateTable(allSummaries);
    }

    private void applyFilters() {
        String selectedDate = (String) dateFilterCombo.getSelectedItem();
        String selectedSection = (String) sectionFilterCombo.getSelectedItem();
        
        if (selectedDate == null || selectedSection == null) return;

        List<AttemptSummary> filtered = allSummaries.stream()
                .filter(s -> {
                    boolean dateMatch = "All Dates".equals(selectedDate);
                    if (!dateMatch && s.getStartTime() != null) {
                        String rowDate = new SimpleDateFormat("yyyy-MM-dd").format(s.getStartTime());
                        dateMatch = rowDate.equals(selectedDate);
                    }
                    boolean sectionMatch = "All Sections".equals(selectedSection);
                    if (!sectionMatch) {
                        sectionMatch = selectedSection.equals(s.getSection());
                    }
                    return dateMatch && sectionMatch;
                })
                .collect(Collectors.toList());
        
        updateTable(filtered);
    }

    private void updateTable(List<AttemptSummary> data) {
        summaryModel.setRowCount(0);
        for (AttemptSummary s : data) {
            summaryModel.addRow(new Object[]{
                    s.getAttemptId(), s.getEnrollmentNumber(), s.getStudentName(),
                    s.getSection(), s.getScore(), s.getFocusLostCount(),
                    s.getStartTime(), s.getEndTime()
            });
        }
    }

    private void onDelete() {
        int row = summaryTable.getSelectedRow();
        if (row < 0) {
            JOptionPane.showMessageDialog(this, "Select a result to delete.");
            return;
        }
        int attemptId = (Integer) summaryTable.getValueAt(row, 0);
        String name = (String) summaryTable.getValueAt(row, 2);

        if (JOptionPane.showConfirmDialog(this, "Delete result for " + name + "?", "Confirm", JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION) {
            if (adminService.deleteExamAttempt(attemptId)) {
                JOptionPane.showMessageDialog(this, "Deleted.");
                loadSummariesAndDates();
            } else {
                JOptionPane.showMessageDialog(this, "Failed to delete.");
            }
        }
    }

    private static class ExamItem {
        final int id; final String title;
        ExamItem(int id, String title) { this.id = id; this.title = title; }
        @Override public String toString() { return "[" + id + "] " + title; }
    }
}