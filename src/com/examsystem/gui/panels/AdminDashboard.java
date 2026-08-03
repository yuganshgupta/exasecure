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
        tabs.addTab("Add Question", new AddQuestionPanel(adminService));
        tabs.addTab("Review Results", new ReviewPanel(adminService));
        tabs.addTab("User Management", new UserManagementPanel(adminService));

        setLayout(new BorderLayout());
        add(tabs, BorderLayout.CENTER);
    }
}