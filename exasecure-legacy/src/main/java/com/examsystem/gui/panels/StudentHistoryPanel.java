package com.examsystem.gui.panels;

import com.examsystem.dao.ExamAttemptDAO;
import com.examsystem.dao.ExamAttemptDAO.ExamAttemptSummary;
import com.examsystem.dao.ExamAttemptDAO.QuestionDetailReport;
import com.examsystem.models.User;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.List;

public class StudentHistoryPanel extends JPanel {

    private final User student;
    private final ExamAttemptDAO attemptDAO = new ExamAttemptDAO();

    private final DefaultTableModel tableModel = new DefaultTableModel(
            new Object[]{"Attempt ID", "Exam Title", "Score", "Percentage", "Status", "Date Taken"}, 0) {
        @Override
        public boolean isCellEditable(int row, int column) {
            return false;
        }
    };
    private final JTable table = new JTable(tableModel);
    private final JButton viewBreakdownBtn = new JButton("View Breakdown");

    public StudentHistoryPanel(User student) {
        this.student = student;
        setLayout(new BorderLayout(10, 10));
        setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // Header
        JPanel header = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JLabel titleLabel = new JLabel("My Exam History");
        titleLabel.setFont(new Font("SansSerif", Font.BOLD, 20));
        JButton refreshBtn = new JButton("Refresh");
        header.add(titleLabel);
        header.add(Box.createHorizontalStrut(20));
        header.add(refreshBtn);

        // Center
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        JScrollPane scrollPane = new JScrollPane(table);

        // Bottom
        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        viewBreakdownBtn.setEnabled(false);
        bottom.add(viewBreakdownBtn);

        add(header, BorderLayout.NORTH);
        add(scrollPane, BorderLayout.CENTER);
        add(bottom, BorderLayout.SOUTH);

        // Listeners
        refreshBtn.addActionListener(e -> loadHistory());
        
        table.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                viewBreakdownBtn.setEnabled(table.getSelectedRow() >= 0);
            }
        });

        viewBreakdownBtn.addActionListener(e -> onViewBreakdown());

        // Initial Load
        loadHistory();
    }

    private void loadHistory() {
        SwingWorker<List<ExamAttemptSummary>, Void> worker = new SwingWorker<List<ExamAttemptSummary>, Void>() {
            @Override
            protected List<ExamAttemptSummary> doInBackground() {
                return attemptDAO.getAttemptsByUserId(student.getId());
            }

            @Override
            protected void done() {
                try {
                    List<ExamAttemptSummary> attempts = get();
                    tableModel.setRowCount(0);
                    for (ExamAttemptSummary s : attempts) {
                        tableModel.addRow(new Object[]{
                                s.getAttemptId(),
                                s.getExamTitle(),
                                s.getScore() + " / " + s.getMaxScore(),
                                String.format("%.2f%%", s.getPercentage()),
                                s.getStatus(),
                                s.getSubmittedAt()
                        });
                    }
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(StudentHistoryPanel.this, 
                        "Failed to load history.", "Error", JOptionPane.ERROR_MESSAGE);
                }
            }
        };
        worker.execute();
    }

    private void onViewBreakdown() {
        int row = table.getSelectedRow();
        if (row < 0) return;

        int attemptId = (Integer) tableModel.getValueAt(row, 0);
        String examTitle = (String) tableModel.getValueAt(row, 1);

        viewBreakdownBtn.setEnabled(false);
        viewBreakdownBtn.setText("Loading...");

        SwingWorker<List<QuestionDetailReport>, Void> worker = new SwingWorker<List<QuestionDetailReport>, Void>() {
            @Override
            protected List<QuestionDetailReport> doInBackground() {
                return attemptDAO.getAttemptDetails(attemptId);
            }

            @Override
            protected void done() {
                viewBreakdownBtn.setEnabled(true);
                viewBreakdownBtn.setText("View Breakdown");
                try {
                    List<QuestionDetailReport> details = get();
                    showBreakdownDialog(examTitle, details);
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(StudentHistoryPanel.this, 
                        "Failed to load attempt details.", "Error", JOptionPane.ERROR_MESSAGE);
                }
            }
        };
        worker.execute();
    }

    private void showBreakdownDialog(String examTitle, List<QuestionDetailReport> details) {
        JDialog dialog = new JDialog(SwingUtilities.getWindowAncestor(this), "Breakdown: " + examTitle, Dialog.ModalityType.APPLICATION_MODAL);
        dialog.setSize(800, 600);
        dialog.setLocationRelativeTo(this);
        dialog.setLayout(new BorderLayout());

        JPanel listPanel = new JPanel();
        listPanel.setLayout(new BoxLayout(listPanel, BoxLayout.Y_AXIS));
        listPanel.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));

        int qNumber = 1;
        for (QuestionDetailReport r : details) {
            JPanel item = new JPanel(new BorderLayout(5, 5));
            item.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Color.LIGHT_GRAY, 1),
                BorderFactory.createEmptyBorder(10, 10, 10, 10)
            ));

            JLabel qLabel = new JLabel("<html><b>Q" + qNumber + ":</b> " + escapeHtml(r.getQuestionText()) + "</html>");
            
            String studentAnsStr = r.getStudentAnswer() == null || r.getStudentAnswer().isEmpty() ? "<i>(No Answer)</i>" : escapeHtml(r.getStudentAnswer());
            JLabel sLabel = new JLabel("<html>Your Answer: " + studentAnsStr + "</html>");
            
            JLabel cLabel = new JLabel("<html>Correct Answer: " + escapeHtml(r.getCorrectAnswer()) + "</html>");
            
            JLabel resultLabel = new JLabel("Result: " + (r.getPointsAwarded() > 0 ? "Correct (+1)" : "Incorrect (0)"));
            resultLabel.setForeground(r.getPointsAwarded() > 0 ? new Color(0, 150, 0) : Color.RED);
            resultLabel.setFont(new Font("SansSerif", Font.BOLD, 14));

            JPanel info = new JPanel(new GridLayout(3, 1, 2, 2));
            info.add(sLabel);
            info.add(cLabel);
            info.add(resultLabel);

            item.add(qLabel, BorderLayout.NORTH);
            item.add(info, BorderLayout.CENTER);
            
            listPanel.add(item);
            listPanel.add(Box.createVerticalStrut(10));
            qNumber++;
        }

        JScrollPane scroll = new JScrollPane(listPanel);
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        dialog.add(scroll, BorderLayout.CENTER);

        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.CENTER));
        JButton closeBtn = new JButton("Close");
        closeBtn.addActionListener(e -> dialog.dispose());
        bottom.add(closeBtn);
        dialog.add(bottom, BorderLayout.SOUTH);

        dialog.setVisible(true);
    }
    
    private static String escapeHtml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;").replace("<", "&lt;")
                   .replace(">", "&gt;").replace("\"", "&quot;");
    }
}
