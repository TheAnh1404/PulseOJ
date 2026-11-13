package com.vku.pulseoj.frontend.ui.client;

import com.vku.pulseoj.backend.client.ClientBackendController;
import net.miginfocom.swing.MigLayout;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Font;

/**
 * [TV3] UDP Auto-Discovery & Contestant Authentication Dialog.
 * Synchronized with Master modern FlatLightLaf design and Vietnamese UI.
 */
public class LanDiscoveryLoginDialog extends JDialog {

    private final ClientBackendController controller;
    private boolean loggedIn = false;

    private final JTextField txtHost = new JTextField("127.0.0.1", 14);
    private final JTextField txtPort = new JTextField("1099", 6);
    private final JButton btnDiscover = new JButton("● Tự Động Tìm Máy Chủ LAN (UDP :8888)");

    private final JTextField txtUsername = new JTextField("student01", 14);
    private final JPasswordField txtPassword = new JPasswordField("password123", 14);
    private final JButton btnLogin = new JButton("► Kết Nối & Vào Phòng Thi");
    private final JLabel lblStatus = new JLabel("Nhấn 'Tự Động Tìm Máy Chủ LAN' hoặc 'Kết Nối' để bắt đầu.");

    public LanDiscoveryLoginDialog(JFrame parent, ClientBackendController controller) {
        super(parent, "PulseOJ — Đăng Nhập Thí Sinh Dự Thi", true);
        this.controller = controller;

        setLayout(new BorderLayout(0, 0));
        setSize(520, 440);
        setResizable(false);
        setLocationRelativeTo(parent);

        // 1. Modern Header Banner
        JPanel headerBanner = new JPanel(new MigLayout("insets 14 20 14 20, fillx", "[grow]", "[][]"));
        headerBanner.setBackground(Color.WHITE);
        headerBanner.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(226, 232, 240)));

        JLabel lblTitle = new JLabel("♦ PULSE OJ — CONTESTANT LOGIN");
        lblTitle.setFont(new Font("Segoe UI", Font.BOLD, 17));
        lblTitle.setForeground(new Color(15, 23, 42));
        lblTitle.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 12));

        JLabel lblSubtitle = new JLabel("Cổng Đăng Nhập Thí Sinh • Tự Động Khám Phá LAN (UDP :8888)");
        lblSubtitle.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        lblSubtitle.setForeground(new Color(100, 116, 139));
        lblSubtitle.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 12));

        headerBanner.add(lblTitle, "wrap");
        headerBanner.add(lblSubtitle);
        add(headerBanner, BorderLayout.NORTH);

        // 2. Body Form
        JPanel bodyPanel = new JPanel(new MigLayout("insets 16 20 16 20, gap 10, fillx", "[][grow]", "[]"));

        // Server Connection Section
        JPanel serverPanel = new JPanel(new MigLayout("insets 8, gap 8, fillx", "[][grow][][]", "[]"));
        serverPanel.setBorder(BorderFactory.createTitledBorder("Kết Nối Máy Chủ Chấm (Master Server)"));
        serverPanel.add(new JLabel("IP Máy chủ:"));
        serverPanel.add(txtHost, "growx");
        serverPanel.add(new JLabel("Cổng RMI:"));
        serverPanel.add(txtPort, "wrap");

        btnDiscover.setFont(new Font("Segoe UI", Font.BOLD, 12));
        btnDiscover.setBackground(new Color(241, 245, 249));
        btnDiscover.setForeground(new Color(30, 41, 59));
        serverPanel.add(btnDiscover, "span 4, growx");
        bodyPanel.add(serverPanel, "span 2, growx, wrap");

        // Credentials Section
        JPanel credPanel = new JPanel(new MigLayout("insets 8, gap 8, fillx", "[][grow]", "[]"));
        credPanel.setBorder(BorderFactory.createTitledBorder("Tài Khoản Thí Sinh Dự Thi"));
        credPanel.add(new JLabel("Tài khoản:"));
        credPanel.add(txtUsername, "growx, wrap");
        credPanel.add(new JLabel("Mật khẩu:"));
        credPanel.add(txtPassword, "growx, wrap");
        bodyPanel.add(credPanel, "span 2, growx, wrap");

        // Status & Action
        lblStatus.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        lblStatus.setForeground(new Color(100, 116, 139));
        bodyPanel.add(lblStatus, "span 2, wrap");

        btnLogin.setFont(new Font("Segoe UI", Font.BOLD, 13));
        btnLogin.setBackground(new Color(37, 99, 235));
        btnLogin.setForeground(Color.WHITE);
        bodyPanel.add(btnLogin, "span 2, growx, h 38!, gaptop 4");

        add(bodyPanel, BorderLayout.CENTER);

        // Event Handlers
        btnDiscover.addActionListener(e -> autoDiscover());
        btnLogin.addActionListener(e -> performLogin());
    }

    private void autoDiscover() {
        btnDiscover.setEnabled(false);
        lblStatus.setText("► Đang phát sóng UDP Broadcast tìm Master trên cổng 8888...");
        lblStatus.setForeground(new Color(37, 99, 235));

        controller.autoDiscoverMaster(2000).thenAccept(result -> {
            SwingUtilities.invokeLater(() -> {
                btnDiscover.setEnabled(true);
                if (result.found()) {
                    txtHost.setText(result.masterHost());
                    txtPort.setText(String.valueOf(result.masterRmiPort()));
                    lblStatus.setText("[OK] Đã tìm thấy Master tại: " + result.masterHost() + ":" + result.masterRmiPort());
                    lblStatus.setForeground(new Color(5, 150, 105));
                } else {
                    lblStatus.setText("[!] Không tìm thấy Master Server nào phản hồi trên mạng LAN.");
                    lblStatus.setForeground(new Color(220, 38, 38));
                }
            });
        });
    }

    private void performLogin() {
        String host = txtHost.getText().trim();
        int port = Integer.parseInt(txtPort.getText().trim());
        String user = txtUsername.getText().trim();
        String pass = new String(txtPassword.getPassword());

        btnLogin.setEnabled(false);
        lblStatus.setText("► Đang kết nối tới Master RMI Server...");
        lblStatus.setForeground(new Color(37, 99, 235));

        controller.connectToMaster(host, port)
                .thenCompose(connected -> {
                    if (!connected) {
                        throw new RuntimeException("Không thể kết nối tới Master RMI Server tại " + host + ":" + port);
                    }
                    SwingUtilities.invokeLater(() -> lblStatus.setText("► Đang xác thực tài khoản thí sinh..."));
                    return controller.loginAsync(user, pass);
                })
                .thenAccept(loginResp -> {
                    SwingUtilities.invokeLater(() -> {
                        btnLogin.setEnabled(true);
                        if (loginResp.isSuccess()) {
                            loggedIn = true;
                            dispose();
                        } else {
                            lblStatus.setText("× " + loginResp.getMessage());
                            lblStatus.setForeground(new Color(220, 38, 38));
                            JOptionPane.showMessageDialog(this, loginResp.getMessage(), "Đăng Nhập Thất Bại", JOptionPane.ERROR_MESSAGE);
                        }
                    });
                })
                .exceptionally(ex -> {
                    SwingUtilities.invokeLater(() -> {
                        btnLogin.setEnabled(true);
                        lblStatus.setText("× Lỗi kết nối: " + ex.getMessage());
                        lblStatus.setForeground(new Color(220, 38, 38));
                        JOptionPane.showMessageDialog(this, ex.getMessage(), "Lỗi Mạng", JOptionPane.ERROR_MESSAGE);
                    });
                    return null;
                });
    }

    public boolean isLoggedIn() {
        return loggedIn;
    }
}
