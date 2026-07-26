package com.examsystem.gui.dialogs;

import com.examsystem.models.AttemptSummary;
import com.examsystem.models.StudentAnswerDetail;
import com.examsystem.services.AdminService;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;

public class AttemptDetailsDialog extends JDialog {

    public AttemptDetailsDialog(Window owner, AdminService service, AttemptSummary summary) {
        super(owner, "Report Card: " + summary.getStudentName(), Dialog.ModalityType.APPLICATION_MODAL);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        
        setSize(900, 600);
        setLocationRelativeTo(owner);
        setLayout(new BorderLayout(10, 10));

        // --- Header ---
        JPanel header = new JPanel(new GridLayout(2, 4, 10, 10));
        header.setBorder(BorderFactory.createTitledBorder("Student Details"));
        header.add(new JLabel("Name: " + summary.getStudentName()));
        header.add(new JLabel("Enrollment: " + summary.getEnrollmentNumber()));
        header.add(new JLabel("Section: " + summary.getSection()));
        header.add(new JLabel("Score: " + summary.getScore()));
        header.add(new JLabel("Time: " + summary.getStartTime()));
        
        // --- CLICKABLE FOCUS LABEL ---
        JLabel focusLabel = new JLabel("Focus Lost: " + summary.getFocusLostCount() + " times (Click for Details)");
        if (summary.getFocusLostCount() > 0) {
            focusLabel.setForeground(Color.RED);
            focusLabel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            focusLabel.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    new ProctorLogViewer(owner, service, summary.getAttemptId()).setVisible(true);
                }
            });
        }
        header.add(focusLabel);
        
        add(header, BorderLayout.NORTH);

        // --- Table ---
        DefaultTableModel model = new DefaultTableModel(new Object[]{"Q#", "Question", "Student Selection", "Correct Answer", "Result"}, 0) {
            @Override public boolean isCellEditable(int row, int col) { return false; }
        };
        JTable table = new JTable(model);
        table.getColumnModel().getColumn(0).setPreferredWidth(30);
        table.getColumnModel().getColumn(1).setPreferredWidth(300);
        
        List<StudentAnswerDetail> details = service.getDetailedAnswers(summary.getAttemptId());
        int qNum = 1;
        for (StudentAnswerDetail d : details) {
            model.addRow(new Object[]{
                qNum++,
                d.getQuestionText(),
                d.getSelectedOptionText() == null ? "(No Answer)" : d.getSelectedOptionText(),
                d.getCorrectOptionText(),
                d.isCorrect() ? "CORRECT" : "WRONG"
            });
        }
        
        add(new JScrollPane(table), BorderLayout.CENTER);

        JButton closeBtn = new JButton("Close");
        closeBtn.addActionListener(e -> dispose());
        JPanel bottom = new JPanel();
        bottom.add(closeBtn);
        add(bottom, BorderLayout.SOUTH);
    }
}