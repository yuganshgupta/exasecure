package com.examsystem.gui;

import com.examsystem.dao.ExamAttemptDAO;
import com.examsystem.dao.ExamDAO;
import com.examsystem.gui.exam.ExamWindow;
import com.examsystem.models.Exam;
import com.examsystem.models.User;
import java.awt.*;
import java.util.List;
import javax.swing.*;
import javax.swing.table.DefaultTableModel;

/** Student Dashboard: lists exams and opens the ExamWindow. */
public class StudentDashboard extends JFrame {

    private final User student;
    private final ExamDAO examDAO = new ExamDAO();
    private final ExamAttemptDAO attemptDAO = new ExamAttemptDAO();

    private final DefaultTableModel model = new DefaultTableModel(new Object[]{"ID", "Title", "Duration (min)", "Created"}, 0) {
        @Override public boolean isCellEditable(int row, int column) { return false; }
    };
    private final JTable table = new JTable(model);
    private List<Exam> currentExams;

    public StudentDashboard(User student) {
        super("Student Dashboard - " + student.getFullName());
        this.student = student;

        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(900, 600);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout());

        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton refreshBtn = new JButton("Refresh");
        JButton takeBtn = new JButton("Take Exam");
        top.add(refreshBtn);
        top.add(takeBtn);

        add(top, BorderLayout.NORTH);
        add(new JScrollPane(table), BorderLayout.CENTER);

        refreshBtn.addActionListener(e -> loadExams());
        takeBtn.addActionListener(e -> onTakeExam());

        loadExams();
    }

    private void loadExams() {
        SwingUtilities.invokeLater(() -> {
            model.setRowCount(0);
            currentExams = examDAO.getAllExams();
            for (Exam ex : currentExams) {
                model.addRow(new Object[]{ex.getId(), ex.getTitle(), ex.getDurationMinutes(), ex.getCreatedAt()});
            }
        });
    }

    private void onTakeExam() {
        int row = table.getSelectedRow();
        if (row < 0) {
            JOptionPane.showMessageDialog(this, "Please select an exam.", "No Selection", JOptionPane.WARNING_MESSAGE);
            return;
        }
        Exam exam = currentExams.get(table.convertRowIndexToModel(row));
        
        // --- NEW ATTEMPT LIMIT LOGIC ---
        int maxAttempts = 1; // Set your maximum allowed attempts here
        int currentAttempts = attemptDAO.getAttemptCount(student.getId(), exam.getId());
        
        if (currentAttempts >= maxAttempts) {
            JOptionPane.showMessageDialog(this, 
                "Access Denied: You have already used your maximum attempts (" + maxAttempts + ") for this exam.", 
                "Limit Reached", 
                JOptionPane.ERROR_MESSAGE);
            return; // Stops the ExamWindow from opening
        }
        // -------------------------------

        new ExamWindow(this, exam, student).setVisible(true);
    }   
}