package com.examsystem.gui.panels;

import com.examsystem.models.User;
import com.examsystem.services.AdminService;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.List;

public class UserManagementPanel extends JPanel {

    private final AdminService adminService;

    private final JTextField usernameField = new JTextField(20);
    private final JPasswordField passwordField = new JPasswordField(20);
    private final JTextField fullNameField = new JTextField(20);
    private final JTextField enrollmentField = new JTextField(20); 
    
    // --- CHANGED: Section is now a ComboBox restricted to A/B ---
    private final JComboBox<String> sectionCombo = new JComboBox<>(new String[]{"A", "B"});
    
    private final JComboBox<String> roleCombo = new JComboBox<>(new String[]{"admin", "student"});

    private final DefaultTableModel model = new DefaultTableModel(
            new Object[]{"ID", "Username", "Full Name", "Enrollment", "Section", "Role"}, 0) {
        @Override public boolean isCellEditable(int row, int column) { return false; }
    };
    private final JTable table = new JTable(model);

    public UserManagementPanel(AdminService adminService) {
        this.adminService = adminService;

        setLayout(new BorderLayout(8, 8));

        JPanel form = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(4, 4, 4, 4);
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;

        int row = 0;
        gbc.gridx = 0; gbc.gridy = row; form.add(new JLabel("Username:"), gbc);
        gbc.gridx = 1; form.add(usernameField, gbc);

        row++;
        gbc.gridx = 0; gbc.gridy = row; form.add(new JLabel("Password:"), gbc);
        gbc.gridx = 1; form.add(passwordField, gbc);

        row++;
        gbc.gridx = 0; gbc.gridy = row; form.add(new JLabel("Full Name:"), gbc);
        gbc.gridx = 1; form.add(fullNameField, gbc);

        row++;
        gbc.gridx = 0; gbc.gridy = row; form.add(new JLabel("Enrollment #:"), gbc);
        gbc.gridx = 1; form.add(enrollmentField, gbc);

        row++;
        gbc.gridx = 0; gbc.gridy = row; form.add(new JLabel("Section:"), gbc);
        gbc.gridx = 1; form.add(sectionCombo, gbc); // Added Combo

        row++;
        gbc.gridx = 0; gbc.gridy = row; form.add(new JLabel("Role:"), gbc);
        gbc.gridx = 1; form.add(roleCombo, gbc);

        row++;
        gbc.gridx = 0; gbc.gridy = row; gbc.gridwidth = 2; gbc.anchor = GridBagConstraints.CENTER;
        
        JPanel btnPanel = new JPanel(new FlowLayout());
        JButton registerBtn = new JButton("Register User");
        JButton updateBtn = new JButton("Update Selected");
        JButton clearBtn = new JButton("Clear Form");
        
        btnPanel.add(registerBtn);
        btnPanel.add(updateBtn);
        btnPanel.add(clearBtn);
        form.add(btnPanel, gbc);

        JPanel top = new JPanel(new BorderLayout());
        top.add(form, BorderLayout.CENTER);

        JPanel btns = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton refreshBtn = new JButton("Refresh Users");
        btns.add(refreshBtn);
        top.add(btns, BorderLayout.SOUTH);

        add(top, BorderLayout.NORTH);
        add(new JScrollPane(table), BorderLayout.CENTER);

        registerBtn.addActionListener(e -> onRegister());
        refreshBtn.addActionListener(e -> loadUsers());
        
        table.getSelectionModel().addListSelectionListener(e -> {
            int selectedRow = table.getSelectedRow();
            if (selectedRow >= 0) {
                usernameField.setText(model.getValueAt(selectedRow, 1).toString());
                usernameField.setEditable(true);
                passwordField.setText("");
                fullNameField.setText(model.getValueAt(selectedRow, 2).toString());
                
                Object enroll = model.getValueAt(selectedRow, 3);
                enrollmentField.setText(enroll == null ? "" : enroll.toString());
                
                // Handle Section Selection
                Object sect = model.getValueAt(selectedRow, 4);
                if (sect != null) {
                    sectionCombo.setSelectedItem(sect.toString());
                }
                
                roleCombo.setSelectedItem(model.getValueAt(selectedRow, 5).toString());
            }
        });

        clearBtn.addActionListener(e -> {
            table.clearSelection();
            usernameField.setText("");
            usernameField.setEditable(true);
            passwordField.setText("");
            fullNameField.setText("");
            enrollmentField.setText("");
            sectionCombo.setSelectedIndex(0);
            roleCombo.setSelectedIndex(1);
        });

        updateBtn.addActionListener(e -> onUpdate());

        loadUsers();
    }

    private void onRegister() {
        String username = usernameField.getText().trim();
        String password = new String(passwordField.getPassword());
        String fullName = fullNameField.getText().trim();
        String enrollment = enrollmentField.getText().trim();
        String section = (String) sectionCombo.getSelectedItem(); // Get from Combo
        String role = (String) roleCombo.getSelectedItem();

        if (username.isEmpty() || password.isEmpty() || fullName.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Username, Password, and Name are required.", "Validation", JOptionPane.WARNING_MESSAGE);
            return;
        }
        
        if (username.length() > 50 || password.length() > 100 || fullName.length() > 100 || enrollment.length() > 50) {
            JOptionPane.showMessageDialog(this, "Input too long. Max lengths: Username(50), Password(100), Name(100), Enrollment(50).", "Validation", JOptionPane.WARNING_MESSAGE);
            return;
        }
        
        if ("student".equalsIgnoreCase(role) && enrollment.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Enrollment number is required for students.", "Validation", JOptionPane.WARNING_MESSAGE);
            return;
        }

        int id = adminService.registerUser(username, password, fullName, enrollment, section, role);
        if (id > 0) {
            JOptionPane.showMessageDialog(this, "User registered! ID: " + id, "Success", JOptionPane.INFORMATION_MESSAGE);
            loadUsers();
        } else {
            JOptionPane.showMessageDialog(this, "Registration failed (Duplicate username?).", "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void onUpdate() {
        int row = table.getSelectedRow();
        if (row < 0) {
            JOptionPane.showMessageDialog(this, "Select a user first.", "Validation", JOptionPane.WARNING_MESSAGE);
            return;
        }
        
        int id = (Integer) model.getValueAt(row, 0);
        String username = usernameField.getText().trim();
        String password = new String(passwordField.getPassword());
        String fullName = fullNameField.getText().trim();
        String enrollment = enrollmentField.getText().trim();
        String section = (String) sectionCombo.getSelectedItem(); // Get from Combo
        String role = (String) roleCombo.getSelectedItem();

        if (username.isEmpty() || fullName.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Username and Name required.", "Validation", JOptionPane.WARNING_MESSAGE);
            return;
        }
        
        if (username.length() > 50 || password.length() > 100 || fullName.length() > 100 || enrollment.length() > 50) {
            JOptionPane.showMessageDialog(this, "Input too long. Max lengths: Username(50), Password(100), Name(100), Enrollment(50).", "Validation", JOptionPane.WARNING_MESSAGE);
            return;
        }

        if ("student".equalsIgnoreCase(role) && enrollment.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Enrollment number is required for students.", "Validation", JOptionPane.WARNING_MESSAGE);
            return;
        }

        if (adminService.updateUser(id, username, password, fullName, enrollment, section, role)) {
            JOptionPane.showMessageDialog(this, "User updated!", "Success", JOptionPane.INFORMATION_MESSAGE);
            loadUsers();
        } else {
            JOptionPane.showMessageDialog(this, "Update failed.", "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void loadUsers() {
        List<User> users = adminService.getAllUsers();
        model.setRowCount(0);
        for (User u : users) {
            model.addRow(new Object[]{
                u.getId(), 
                u.getUsername(), 
                u.getFullName(), 
                u.getEnrollmentNumber(), 
                u.getSection(), 
                u.getRole()
            });
        }
    }
}