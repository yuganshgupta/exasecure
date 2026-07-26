package com.examsystem.gui.dialogs;

import com.examsystem.models.ProctorLog;
import com.examsystem.services.AdminService;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.List;

public class ProctorLogViewer extends JDialog {

    public ProctorLogViewer(Window owner, AdminService service, int attemptId) {
        super(owner, "Proctoring Log - Attempt " + attemptId, Dialog.ModalityType.APPLICATION_MODAL);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setSize(600, 400);
        setLocationRelativeTo(owner);
        
        DefaultTableModel model = new DefaultTableModel(new Object[]{"Timestamp", "Event"}, 0) {
            @Override public boolean isCellEditable(int row, int col) { return false; }
        };
        JTable table = new JTable(model);
        table.getColumnModel().getColumn(0).setPreferredWidth(150);
        table.getColumnModel().getColumn(1).setPreferredWidth(400);
        
        List<ProctorLog> logs = service.getProctorLogs(attemptId);
        if (logs.isEmpty()) {
            model.addRow(new Object[]{"", "No violations recorded."});
        } else {
            for (ProctorLog log : logs) {
                model.addRow(new Object[]{log.getTimestamp(), log.getMessage()});
            }
        }
        
        add(new JScrollPane(table));
    }
}