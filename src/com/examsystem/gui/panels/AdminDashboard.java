package com.examsystem.gui.panels;

import com.examsystem.models.User;
import com.examsystem.services.AdminService;
import java.awt.*;
import javax.swing.*;

/** Admin Dashboard with a tabbed interface. */
public class AdminDashboard extends JFrame {

    private final AdminService adminService = new AdminService();

    public AdminDashboard(User adminUser) {
        // Here adminUser refers directly to the constructor parameter, which is perfectly fine.
        super("Admin Dashboard - " + adminUser.getFullName());

        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1000, 700);
        setLocationRelativeTo(null);

        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("Exam Management", new ExamManagementPanel(adminUser, adminService));
        tabs.addTab("Question Management", new QuestionManagementPanel(adminService));
        tabs.addTab("Review Results", new ReviewPanel(adminService));
        tabs.addTab("User Management", new UserManagementPanel(adminService));

        JPanel topBar = new JPanel(new BorderLayout());
        JLabel welcomeLabel = new JLabel("  Admin Dashboard - " + adminUser.getFullName());
        welcomeLabel.setFont(new Font("SansSerif", Font.BOLD, 18));
        topBar.add(welcomeLabel, BorderLayout.WEST);

        JPanel logoutWrapper = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton logoutBtn = new JButton("Logout");
        logoutBtn.addActionListener(e -> {
            dispose();
            new com.examsystem.gui.LoginWindow().setVisible(true);
        });
        logoutWrapper.add(logoutBtn);
        topBar.add(logoutWrapper, BorderLayout.EAST);

        setLayout(new BorderLayout());
        add(topBar, BorderLayout.NORTH);
        add(tabs, BorderLayout.CENTER);
    }
}