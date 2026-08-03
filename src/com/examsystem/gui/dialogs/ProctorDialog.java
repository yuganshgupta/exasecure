package com.examsystem.gui.dialogs;

import com.examsystem.models.StudentAnswer;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.sql.Timestamp;
import java.util.List;

/** Dialog showing chronological answers with per-answer delta seconds. */
public class ProctorDialog extends JDialog {

    private final DefaultTableModel model = new DefaultTableModel(
            new Object[]{"#", "Question ID", "Selected", "Correct", "Timestamp", "Δ seconds"}, 0) {
        @Override public boolean isCellEditable(int row, int column) { return false; }
    };

    public ProctorDialog(Window owner, int attemptId, List<StudentAnswer> answers) {
        super(owner, "Proctoring - Attempt " + attemptId, ModalityType.APPLICATION_MODAL);
        setSize(800, 500);
        setLocationRelativeTo(owner);
        setLayout(new BorderLayout());

        JTable table = new JTable(model);
        add(new JScrollPane(table), BorderLayout.CENTER);

        populate(answers);
    }

    private void populate(List<StudentAnswer> answers) {
        model.setRowCount(0);
        Timestamp prev = null;
        for (int i = 0; i < answers.size(); i++) {
            StudentAnswer a = answers.get(i);
            Timestamp ts = a.getAnswerTimestamp();
            long deltaSec = prev == null ? 0 : (ts.getTime() - prev.getTime()) / 1000;
            prev = ts;

            model.addRow(new Object[]{
                    i + 1, a.getQuestionId(), a.getSelectedOptions(),
                    a.isCorrect() ? "YES" : "NO", ts, deltaSec
            });
        }
    }
}