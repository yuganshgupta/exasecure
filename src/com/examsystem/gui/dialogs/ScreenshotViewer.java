package com.examsystem.gui.dialogs;

import com.examsystem.services.AdminService;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.util.List;

public class ScreenshotViewer extends JDialog {

    private final JLabel imageLabel = new JLabel();
    private final JList<File> fileList = new JList<>();

    public ScreenshotViewer(Window owner, AdminService service, int attemptId) {
        super(owner, "Proctor Evidence - Attempt " + attemptId, Dialog.ModalityType.APPLICATION_MODAL);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setSize(1000, 700);
        setLocationRelativeTo(owner);
        setLayout(new BorderLayout());

        // Get files
        List<File> files = service.getProctorScreenshots(attemptId);

        if (files.isEmpty()) {
            add(new JLabel("No screenshots found for this attempt.", SwingConstants.CENTER), BorderLayout.CENTER);
            return;
        }

        // --- Left Panel: List of Files ---
        fileList.setListData(files.toArray(new File[0]));
        fileList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        // Custom renderer to show just the name, not the full path
        fileList.setCellRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                if (value instanceof File) {
                    setText(((File) value).getName());
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

    private void showImage(File file) {
        if (file == null || !file.exists()) return;
        
        try {
            ImageIcon originalIcon = new ImageIcon(file.getPath());
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