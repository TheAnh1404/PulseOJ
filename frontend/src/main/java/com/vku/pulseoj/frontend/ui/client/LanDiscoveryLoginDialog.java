package com.vku.pulseoj.frontend.ui.client;

import com.vku.pulseoj.backend.client.ClientBackendController;
import com.vku.pulseoj.backend.discovery.UdpDiscoveryClient;
import com.vku.pulseoj.common.dto.LoginResponseDTO;
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
import java.awt.Color;
import java.awt.Font;
import java.util.concurrent.CompletableFuture;

/**
 * [TV3] UDP Auto-Discovery & Contestant Authentication Dialog.
 */
public class LanDiscoveryLoginDialog extends JDialog {

    private final ClientBackendController controller;
    private boolean loggedIn = false;

    private final JTextField txtHost = new JTextField("127.0.0.1", 14);
    private final JTextField txtPort = new JTextField("1099", 6);
    private final JButton btnDiscover = new JButton("Auto-Discover (UDP 8888)");

    private final JTextField txtUsername = new JTextField("student01", 14);
    private final JPasswordField txtPassword = new JPasswordField("password123", 14);
    private final JButton btnLogin = new JButton("Connect & Login");
    private final JLabel lblStatus = new JLabel("Click Auto-Discover or Login to start.");

    public LanDiscoveryLoginDialog(JFrame parent, ClientBackendController controller) {
        super(parent, "PulseOJ - Contestant Login", true);
        this.controller = controller;

        setLayout(new MigLayout("insets 20, gap 12", "[][grow]", "[]"));
        setSize(460, 320);
        setResizable(false);
        setLocationRelativeTo(parent);

        // Header Title
        JLabel lblTitle = new JLabel("PulseOJ Contestant Workspace");
        lblTitle.setFont(lblTitle.getFont().deriveFont(Font.BOLD, 16f));
        add(lblTitle, "span 2, align center, gapbottom 10, wrap");

        // Server Connection Section
        JPanel serverPanel = new JPanel(new MigLayout("insets 8, gap 8", "[][grow][]", "[]"));
        serverPanel.setBorder(BorderFactory.createTitledBorder("Master Server Connection"));
        serverPanel.add(new JLabel("Host:"));
        serverPanel.add(txtHost, "growx");
        serverPanel.add(new JLabel("Port:"));
        serverPanel.add(txtPort, "wrap");
        serverPanel.add(btnDiscover, "span 4, growx");
        add(serverPanel, "span 2, growx, wrap");

        // Credentials Section
        JPanel credPanel = new JPanel(new MigLayout("insets 8, gap 8", "[][grow]", "[]"));
        credPanel.setBorder(BorderFactory.createTitledBorder("Contestant Account"));
        credPanel.add(new JLabel("Username:"));
        credPanel.add(txtUsername, "growx, wrap");
        credPanel.add(new JLabel("Password:"));
        credPanel.add(txtPassword, "growx, wrap");
        add(credPanel, "span 2, growx, wrap");

        // Status & Action
        lblStatus.setFont(lblStatus.getFont().deriveFont(11f));
        lblStatus.setForeground(Color.GRAY);
        add(lblStatus, "span 2, wrap");

        btnLogin.setFont(btnLogin.getFont().deriveFont(Font.BOLD, 13f));
        btnLogin.setBackground(new Color(46, 204, 113));
        btnLogin.setForeground(Color.WHITE);
        add(btnLogin, "span 2, growx, gaptop 5");

        // Event Handlers
        btnDiscover.addActionListener(e -> autoDiscover());
        btnLogin.addActionListener(e -> performLogin());
    }

    private void autoDiscover() {
        btnDiscover.setEnabled(false);
        lblStatus.setText("Scanning LAN via UDP broadcast on port 8888...");
        lblStatus.setForeground(new Color(52, 152, 219));

        controller.autoDiscoverMaster(2000).thenAccept(result -> {
            SwingUtilities.invokeLater(() -> {
                btnDiscover.setEnabled(true);
                if (result.found()) {
                    txtHost.setText(result.masterHost());
                    txtPort.setText(String.valueOf(result.masterRmiPort()));
                    lblStatus.setText("Discovered: " + result.masterHost() + ":" + result.masterRmiPort());
                    lblStatus.setForeground(new Color(46, 204, 113));
                } else {
                    lblStatus.setText("No Master Server responded on LAN.");
                    lblStatus.setForeground(new Color(231, 76, 60));
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
        lblStatus.setText("Connecting to Master Server...");
        lblStatus.setForeground(new Color(52, 152, 219));

        controller.connectToMaster(host, port)
                .thenCompose(connected -> {
                    if (!connected) {
                        throw new RuntimeException("Could not connect to Master RMI Server.");
                    }
                    SwingUtilities.invokeLater(() -> lblStatus.setText("Authenticating credentials..."));
                    return controller.loginAsync(user, pass);
                })
                .thenAccept(loginResp -> {
                    SwingUtilities.invokeLater(() -> {
                        btnLogin.setEnabled(true);
                        if (loginResp.isSuccess()) {
                            loggedIn = true;
                            dispose();
                        } else {
                            lblStatus.setText(loginResp.getMessage());
                            lblStatus.setForeground(new Color(231, 76, 60));
                            JOptionPane.showMessageDialog(this, loginResp.getMessage(), "Login Failed", JOptionPane.ERROR_MESSAGE);
                        }
                    });
                })
                .exceptionally(ex -> {
                    SwingUtilities.invokeLater(() -> {
                        btnLogin.setEnabled(true);
                        lblStatus.setText("Connection failed: " + ex.getMessage());
                        lblStatus.setForeground(new Color(231, 76, 60));
                        JOptionPane.showMessageDialog(this, ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
                    });
                    return null;
                });
    }

    public boolean isLoggedIn() {
        return loggedIn;
    }
}
