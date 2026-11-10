package com.vku.pulseoj.frontend.ui.master;

import com.vku.pulseoj.backend.master.MasterBackendController;
import com.vku.pulseoj.common.dto.ContestantDTO;
import net.miginfocom.swing.MigLayout;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * [TV1] Contestant Account Management Panel (Neon PostgreSQL).
 * Allows Teacher/Master to create contestant accounts, lock/unlock them, and view records.
 */
public class ContestantManagementPanel extends JPanel {

    private final MasterBackendController controller;
    private final DefaultTableModel tableModel;
    private final JTable table;

    private final JTextField txtUsername = new JTextField(15);
    private final JPasswordField txtPassword = new JPasswordField(15);
    private final JTextField txtFullName = new JTextField(15);
    private final JButton btnCreate = new JButton("Create Account");
    private final JButton btnToggleLock = new JButton("Toggle Lock");
    private final JButton btnRefresh = new JButton("Refresh");

    public ContestantManagementPanel(MasterBackendController controller) {
        this.controller = controller;
        setLayout(new BorderLayout(10, 10));
        setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // 1. Table
        String[] columns = {"ID", "Username", "Full Name", "Status", "Registered At"};
        tableModel = new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        table = new JTable(tableModel);
        table.setRowHeight(26);
        add(new JScrollPane(table), BorderLayout.CENTER);

        // 2. Bottom Input Panel for adding contestants
        JPanel bottomPanel = new JPanel(new MigLayout("insets 10, gap 10", "[][grow][][grow][][grow][]", "[]"));
        bottomPanel.setBorder(BorderFactory.createTitledBorder("Register New Contestant (Saved to Neon Cloud)"));

        bottomPanel.add(new JLabel("Username:"));
        bottomPanel.add(txtUsername, "growx");
        bottomPanel.add(new JLabel("Password:"));
        bottomPanel.add(txtPassword, "growx");
        bottomPanel.add(new JLabel("Full Name:"));
        bottomPanel.add(txtFullName, "growx");
        bottomPanel.add(btnCreate, "wrap");

        // Action toolbar
        JPanel actionPanel = new JPanel(new MigLayout("insets 5, gap 10"));
        actionPanel.add(btnToggleLock);
        actionPanel.add(btnRefresh);

        JPanel southContainer = new JPanel(new BorderLayout());
        southContainer.add(bottomPanel, BorderLayout.CENTER);
        southContainer.add(actionPanel, BorderLayout.SOUTH);
        add(southContainer, BorderLayout.SOUTH);

        // Event Handlers
        btnCreate.addActionListener(e -> createContestantAction());
        btnToggleLock.addActionListener(e -> toggleLockAction());
        btnRefresh.addActionListener(e -> refreshTableAsync());

        // Initial load
        refreshTableAsync();
    }

    public void refreshTableAsync() {
        CompletableFuture.runAsync(() -> {
            List<ContestantDTO> contestants = controller.getAllContestants();
            SwingUtilities.invokeLater(() -> {
                tableModel.setRowCount(0);
                for (ContestantDTO c : contestants) {
                    tableModel.addRow(new Object[]{
                            c.getId(),
                            c.getUsername(),
                            c.getFullName(),
                            c.isLocked() ? "LOCKED" : "ACTIVE",
                            c.getCreatedAt() != null ? c.getCreatedAt().toString() : "N/A"
                    });
                }
            });
        });
    }

    private void createContestantAction() {
        String username = txtUsername.getText().trim();
        String password = new String(txtPassword.getPassword());
        String fullName = txtFullName.getText().trim();

        if (username.isEmpty() || password.isEmpty() || fullName.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Please fill in all fields.", "Validation Error", JOptionPane.WARNING_MESSAGE);
            return;
        }

        btnCreate.setEnabled(false);
        CompletableFuture.runAsync(() -> {
            try {
                controller.createContestant(username, password, fullName);
                SwingUtilities.invokeLater(() -> {
                    txtUsername.setText("");
                    txtPassword.setText("");
                    txtFullName.setText("");
                    btnCreate.setEnabled(true);
                    JOptionPane.showMessageDialog(this, "Contestant created successfully!", "Success", JOptionPane.INFORMATION_MESSAGE);
                    refreshTableAsync();
                });
            } catch (Exception ex) {
                SwingUtilities.invokeLater(() -> {
                    btnCreate.setEnabled(true);
                    JOptionPane.showMessageDialog(this, "Failed to create contestant: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
                });
            }
        });
    }

    private void toggleLockAction() {
        int selectedRow = table.getSelectedRow();
        if (selectedRow < 0) {
            JOptionPane.showMessageDialog(this, "Select a contestant from the table first.", "Note", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        int id = (int) tableModel.getValueAt(selectedRow, 0);
        String currentStatus = (String) tableModel.getValueAt(selectedRow, 3);
        boolean newLocked = !"LOCKED".equalsIgnoreCase(currentStatus);

        CompletableFuture.runAsync(() -> {
            controller.setContestantLock(id, newLocked);
            refreshTableAsync();
        });
    }
}
