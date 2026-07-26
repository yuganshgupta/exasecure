package com.examsystem.gui;

import com.examsystem.models.User;
import com.examsystem.services.AdminService;
import com.examsystem.gui.panels.AddQuestionPanel;
import com.examsystem.gui.panels.CreateExamPanel;
import com.examsystem.gui.panels.ReviewPanel;
import com.examsystem.gui.panels.UserManagementPanel;

import javax.swing.*;
import java.awt.*;

/** Admin Dashboard with a tabbed interface. */
public class AdminDashboard extends JFrame {

    private final User adminUser;
    private final AdminService adminService = new AdminService();

    public AdminDashboard(User adminUser) {
        super("Admin Dashboard - " + adminUser.getFullName());
        this.adminUser = adminUser;

        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1000, 700);
        setLocationRelativeTo(null);

        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("Create Exam", new CreateExamPanel(adminUser, adminService));
        tabs.addTab("Add Question", new AddQuestionPanel(adminService));
        tabs.addTab("Review Results", new ReviewPanel(adminService));
        tabs.addTab("User Management", new UserManagementPanel(adminService));

        setLayout(new BorderLayout());
        add(tabs, BorderLayout.CENTER);
    }
}