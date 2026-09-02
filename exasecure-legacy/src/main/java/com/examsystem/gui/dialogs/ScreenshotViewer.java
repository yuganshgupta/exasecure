package com.examsystem.gui.dialogs;

import com.examsystem.models.ProctorLog;
import com.examsystem.services.AdminService;

import javax.swing.*;
import java.awt.*;
import java.util.List;

public class ScreenshotViewer extends JDialog {

    private final JLabel imageLabel = new JLabel();
    private final JList<ProctorLog> fileList = new JList<>();

    public ScreenshotViewer(Window owner, AdminService service, int attemptId) {
        super(owner, "Proctor Evidence - Attempt " + attemptId, Dialog.ModalityType.APPLICATION_MODAL);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setSize(1000, 700);
        setLocationRelativeTo(owner);
        setLayout(new BorderLayout());

        // Get logs
        List<ProctorLog> logs = service.getProctorLogs(attemptId);
        List<ProctorLog> screenshotLogs = new java.util.ArrayList<>();
        for (ProctorLog log : logs) {
            if (log.getScreenshotData() != null) {
                screenshotLogs.add(log);
            }
        }

        if (screenshotLogs.isEmpty()) {
            add(new JLabel("No screenshots found for this attempt.", SwingConstants.CENTER), BorderLayout.CENTER);
            return;
        }

        // --- Left Panel: List of Logs ---
        fileList.setListData(screenshotLogs.toArray(new ProctorLog[0]));
        fileList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        // Custom renderer to show timestamp
        fileList.setCellRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                if (value instanceof ProctorLog) {
                    setText(((ProctorLog) value).getTimestamp().toString());
                }
                return this;
            }
        });
        
        fileList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                showImage(fileList.getSelectedValue());
            }
        });

        JScrollPane listScroll = new JScrollPane(fileList);
        listScroll.setPreferredSize(new Dimension(250, 0));
        listScroll.setBorder(BorderFactory.createTitledBorder("Violation Events"));
        add(listScroll, BorderLayout.WEST);

        // --- Center Panel: Image ---
        imageLabel.setHorizontalAlignment(SwingConstants.CENTER);
        JScrollPane imageScroll = new JScrollPane(imageLabel);
        add(imageScroll, BorderLayout.CENTER);

        // Select first by default
        fileList.setSelectedIndex(0);
    }

    private void showImage(ProctorLog log) {
        if (log == null || log.getScreenshotData() == null) return;
        
        try {
            ImageIcon originalIcon = new ImageIcon(log.getScreenshotData());
            // Scale image to fit reasonably if huge
            Image img = originalIcon.getImage();
            
            // Simple logic: Scale down if wider than 800
            int w = originalIcon.getIconWidth();
            int h = originalIcon.getIconHeight();
            
            if (w > 800) {
                int newH = (h * 800) / w;
                img = img.getScaledInstance(800, newH, Image.SCALE_SMOOTH);
            }
            
            imageLabel.setIcon(new ImageIcon(img));
            imageLabel.setText(""); // clear text
        } catch (Exception e) {
            imageLabel.setIcon(null);
            imageLabel.setText("Error loading image.");
        }
    }
}