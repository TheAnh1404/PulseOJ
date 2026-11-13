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
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
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
    private final JLabel lblStats = new JLabel("Tổng số thí sinh: 0 | Đang hoạt động: 0 | Bị khóa: 0");

    private final JTextField txtUsername = new JTextField(15);
    private final JPasswordField txtPassword = new JPasswordField(15);
    private final JTextField txtFullName = new JTextField(15);
    private final JButton btnCreate = new JButton("+ Tạo Tài Khoản Thí Sinh");
    private final JButton btnToggleLock = new JButton("■ Khóa / Mở Khóa");
    private final JButton btnRefresh = new JButton("► Làm Mới Danh Sách");

    public ContestantManagementPanel(MasterBackendController controller) {
        this.controller = controller;
        setLayout(new BorderLayout(10, 10));
        setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));

        // Top Summary Bar
        JPanel topBar = new JPanel(new MigLayout("insets 4 8 8 8, fillx", "[grow][]", "[]"));
        lblStats.setFont(new Font("Segoe UI", Font.BOLD, 13));
        lblStats.setForeground(new Color(51, 65, 85));
        topBar.add(lblStats, "align left");
        add(topBar, BorderLayout.NORTH);

        // 1. Table
        String[] columns = {"ID", "Tên Đăng Nhập", "Họ và Tên", "Trạng Thái", "Ngày Tạo"};
        tableModel = new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        table = new JTable(tableModel);
        table.setRowHeight(30);

        // Status Column Renderer with soft colored pills
        table.getColumnModel().getColumn(3).setCellRenderer(new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
                Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
                if (value != null && !isSelected) {
                    String status = value.toString();
                    if ("ACTIVE".equalsIgnoreCase(status) || "HOẠT ĐỘNG".equalsIgnoreCase(status)) {
                        c.setForeground(new Color(22, 101, 52)); // Deep green
                        c.setBackground(new Color(220, 252, 231)); // Soft green
                    } else {
                        c.setForeground(new Color(153, 27, 27)); // Deep red
                        c.setBackground(new Color(254, 226, 226)); // Soft red
                    }
                    c.setFont(c.getFont().deriveFont(Font.BOLD, 12f));
                }
                return c;
            }
        });

        JScrollPane scrollPane = new JScrollPane(table);
        scrollPane.setBorder(BorderFactory.createLineBorder(new Color(226, 232, 240), 1));
        add(scrollPane, BorderLayout.CENTER);

        // 2. Bottom Input Panel for adding contestants
        JPanel bottomPanel = new JPanel(new MigLayout("insets 12 16 12 16, gap 10", "[][grow][][grow][][grow][]", "[]"));
        bottomPanel.setBackground(new Color(248, 250, 252));
        bottomPanel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createTitledBorder("Đăng Ký Thí Sinh Mới (Lưu Bền Vững Vào Neon Cloud PostgreSQL)"),
                BorderFactory.createEmptyBorder(6, 6, 6, 6)
        ));

        bottomPanel.add(new JLabel("Tên đăng nhập:"));
        bottomPanel.add(txtUsername, "growx");
        bottomPanel.add(new JLabel("Mật khẩu:"));
        bottomPanel.add(txtPassword, "growx");
        bottomPanel.add(new JLabel("Họ tên:"));
        bottomPanel.add(txtFullName, "growx");

        btnCreate.setBackground(new Color(37, 99, 235)); // Vibrant blue
        btnCreate.setForeground(Color.WHITE);
        btnCreate.setFont(new Font("Segoe UI", Font.BOLD, 12));
        bottomPanel.add(btnCreate, "wrap");

        // Action toolbar
        JPanel actionPanel = new JPanel(new MigLayout("insets 8 4 4 4, gap 10"));
        btnToggleLock.setBackground(new Color(239, 68, 68)); // Red
        btnToggleLock.setForeground(Color.WHITE);
        btnToggleLock.setFont(new Font("Segoe UI", Font.BOLD, 12));

        btnRefresh.setBackground(new Color(241, 245, 249));
        btnRefresh.setForeground(new Color(51, 65, 85));

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
            long activeCount = contestants.stream().filter(c -> !c.isLocked()).count();
            long lockedCount = contestants.size() - activeCount;

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
                lblStats.setText(String.format("Tổng số thí sinh: %d  |  ● Đang hoạt động: %d  |  ● Bị khóa: %d",
                        contestants.size(), activeCount, lockedCount));
            });
        });
    }

    private void createContestantAction() {
        String username = txtUsername.getText().trim();
        String password = new String(txtPassword.getPassword());
        String fullName = txtFullName.getText().trim();

        if (username.isEmpty() || password.isEmpty() || fullName.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Vui lòng nhập đầy đủ Tên đăng nhập, Mật khẩu và Họ tên.", "Thiếu Thông Tin", JOptionPane.WARNING_MESSAGE);
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
                    JOptionPane.showMessageDialog(this, "Tạo tài khoản thí sinh thành công!", "Thành Công", JOptionPane.INFORMATION_MESSAGE);
                    refreshTableAsync();
                });
            } catch (Exception ex) {
                SwingUtilities.invokeLater(() -> {
                    btnCreate.setEnabled(true);
                    JOptionPane.showMessageDialog(this, "Không thể tạo tài khoản: " + ex.getMessage(), "Lỗi Hệ Thống", JOptionPane.ERROR_MESSAGE);
                });
            }
        });
    }

    private void toggleLockAction() {
        int selectedRow = table.getSelectedRow();
        if (selectedRow < 0) {
            JOptionPane.showMessageDialog(this, "Vui lòng chọn một thí sinh trong bảng trước.", "Thông Báo", JOptionPane.INFORMATION_MESSAGE);
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
