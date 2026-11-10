package com.vku.pulseoj.frontend.ui.worker;

import com.vku.pulseoj.backend.discovery.UdpDiscoveryClient;
import com.vku.pulseoj.backend.worker.WorkerBackendController;
import com.vku.pulseoj.backend.worker.WorkerUiListener;
import com.vku.pulseoj.common.dto.JudgeResultDTO;
import com.vku.pulseoj.common.dto.SubmissionDTO;
import com.vku.pulseoj.common.enums.WorkerStatus;
import net.miginfocom.swing.MigLayout;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.util.concurrent.CompletableFuture;

/**
 * [TV2] Judge Worker Application Window.
 * Displays local execution sandbox console, real-time test progress, and crash simulation button.
 */
public class WorkerMainFrame extends JFrame implements WorkerUiListener {

    private final WorkerBackendController controller;

    private final JTextField txtMasterHost = new JTextField("127.0.0.1", 12);
    private final JTextField txtMasterPort = new JTextField("1099", 6);
    private final JButton btnDiscover = new JButton("Auto-Discover (UDP 8888)");
    private final JButton btnConnect = new JButton("Connect to Master");
    private final JButton btnCrash = new JButton("Simulate Crash (Kill Node)");

    private final JLabel lblStatus = new JLabel("OFFLINE");
    private final JLabel lblCurrentTask = new JLabel("Waiting for judging task...");
    private final JProgressBar pbTestProgress = new JProgressBar(0, 100);
    private final JTextArea txtConsole = new JTextArea();

    public WorkerMainFrame(WorkerBackendController controller) {
        super("PulseOJ - Judge Worker [" + controller.getWorkerId() + "]");
        this.controller = controller;

        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(850, 620);
        setMinimumSize(new Dimension(750, 480));
        setLocationRelativeTo(null);

        controller.setUiListener(this);

        setLayout(new BorderLayout(10, 10));

        // 1. Top Connection Bar
        JPanel topBar = new JPanel(new MigLayout("insets 10, gap 10", "[][][][][grow][]", "[]"));
        topBar.setBorder(BorderFactory.createTitledBorder("Cluster Connection Settings"));

        topBar.add(new JLabel("Master Host:"));
        topBar.add(txtMasterHost);
        topBar.add(new JLabel("Port:"));
        topBar.add(txtMasterPort);
        topBar.add(btnDiscover);
        topBar.add(btnConnect);

        btnCrash.setBackground(new Color(192, 57, 43));
        btnCrash.setForeground(Color.WHITE);
        btnCrash.setFont(btnCrash.getFont().deriveFont(Font.BOLD));
        btnCrash.setEnabled(false);
        topBar.add(btnCrash, "align right");

        add(topBar, BorderLayout.NORTH);

        // 2. Center Panel (Status, Task Progress, Console)
        JPanel centerPanel = new JPanel(new BorderLayout(10, 10));
        centerPanel.setBorder(BorderFactory.createEmptyBorder(0, 10, 10, 10));

        // Task & Progress header
        JPanel progressHeader = new JPanel(new MigLayout("insets 10, gap 10, fillx", "[grow][]", "[]"));
        progressHeader.setBorder(BorderFactory.createTitledBorder("Active Judging Job"));

        lblCurrentTask.setFont(lblCurrentTask.getFont().deriveFont(Font.BOLD, 13f));
        progressHeader.add(lblCurrentTask, "wrap");

        pbTestProgress.setStringPainted(true);
        pbTestProgress.setString("Idle");
        progressHeader.add(pbTestProgress, "growx");

        lblStatus.setFont(lblStatus.getFont().deriveFont(Font.BOLD, 14f));
        lblStatus.setForeground(new Color(231, 76, 60));
        progressHeader.add(lblStatus, "align right");

        centerPanel.add(progressHeader, BorderLayout.NORTH);

        // Console area
        txtConsole.setEditable(false);
        txtConsole.setFont(new Font("Consolas", Font.PLAIN, 12));
        txtConsole.setBackground(new Color(30, 30, 30));
        txtConsole.setForeground(new Color(220, 220, 220));
        JScrollPane consoleScroll = new JScrollPane(txtConsole);
        consoleScroll.setBorder(BorderFactory.createTitledBorder("ProcessBuilder Sandbox Console Output"));
        centerPanel.add(consoleScroll, BorderLayout.CENTER);

        add(centerPanel, BorderLayout.CENTER);

        // Event Listeners
        btnDiscover.addActionListener(e -> autoDiscoverMaster());
        btnConnect.addActionListener(e -> toggleConnection());
        btnCrash.addActionListener(e -> simulateCrash());
    }

    private void autoDiscoverMaster() {
        btnDiscover.setEnabled(false);
        txtConsole.append(">>> Broadcasting UDP Discovery probe (timeout 2000 ms)...\n");

        CompletableFuture.runAsync(() -> {
            UdpDiscoveryClient.DiscoveryResult result = controller.autoDiscoverMaster(2000);
            SwingUtilities.invokeLater(() -> {
                btnDiscover.setEnabled(true);
                if (result.found()) {
                    txtMasterHost.setText(result.masterHost());
                    txtMasterPort.setText(String.valueOf(result.masterRmiPort()));
                    txtConsole.append(">>> Auto-discovered Master Server at: " + result.masterHost() + ":" + result.masterRmiPort() + "\n");
                } else {
                    txtConsole.append(">>> Discovery failed: " + result.message() + "\n");
                    JOptionPane.showMessageDialog(this, result.message(), "Discovery", JOptionPane.INFORMATION_MESSAGE);
                }
            });
        });
    }

    private void toggleConnection() {
        if (!controller.isConnected()) {
            String host = txtMasterHost.getText().trim();
            int port = Integer.parseInt(txtMasterPort.getText().trim());

            btnConnect.setEnabled(false);
            txtConsole.append(">>> Connecting to Master at " + host + ":" + port + "...\n");

            CompletableFuture.runAsync(() -> {
                boolean ok = controller.connectToMaster(host, port);
                SwingUtilities.invokeLater(() -> {
                    btnConnect.setEnabled(true);
                    if (ok) {
                        btnConnect.setText("Disconnect");
                        btnCrash.setEnabled(true);
                        txtMasterHost.setEnabled(false);
                        txtMasterPort.setEnabled(false);
                        btnDiscover.setEnabled(false);
                    } else {
                        JOptionPane.showMessageDialog(this, "Failed to connect to Master RMI Server.", "Connection Error", JOptionPane.ERROR_MESSAGE);
                    }
                });
            });
        } else {
            controller.disconnect();
            btnConnect.setText("Connect to Master");
            btnCrash.setEnabled(false);
            txtMasterHost.setEnabled(true);
            txtMasterPort.setEnabled(true);
            btnDiscover.setEnabled(true);
            lblStatus.setText("OFFLINE");
            lblStatus.setForeground(new Color(231, 76, 60));
        }
    }

    private void simulateCrash() {
        int confirm = JOptionPane.showConfirmDialog(this,
                "Simulate an abrupt Worker crash?\n" +
                "This will immediately terminate the UDP Heartbeat and drop RMI stubs.\n" +
                "Master will detect timeout (>6s) and failover in-flight jobs.",
                "Confirm Crash Simulation", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);

        if (confirm == JOptionPane.YES_OPTION) {
            controller.simulateCrash();
            btnConnect.setText("Connect to Master");
            btnCrash.setEnabled(false);
            txtMasterHost.setEnabled(true);
            txtMasterPort.setEnabled(true);
            btnDiscover.setEnabled(true);
        }
    }

    // =========================================================================
    // WorkerUiListener Implementation (Dispatched safely on Swing EDT)
    // =========================================================================

    @Override
    public void onWorkerStatusChanged(WorkerStatus status) {
        SwingUtilities.invokeLater(() -> {
            lblStatus.setText(status.name());
            if (status == WorkerStatus.IDLE) {
                lblStatus.setForeground(new Color(46, 204, 113));
                pbTestProgress.setString("Idle - Ready");
            } else if (status == WorkerStatus.BUSY) {
                lblStatus.setForeground(new Color(52, 152, 219));
            } else {
                lblStatus.setForeground(new Color(231, 76, 60));
                pbTestProgress.setString("Offline");
            }
        });
    }

    @Override
    public void onJobReceived(SubmissionDTO submission) {
        SwingUtilities.invokeLater(() -> {
            lblCurrentTask.setText(String.format("Judging Sub #%d | Problem: %s | Lang: %s | User: %s",
                    submission.getId(), submission.getProblemCode(), submission.getLanguage(), submission.getContestantUsername()));
            pbTestProgress.setValue(0);
            pbTestProgress.setString("Compiling...");
        });
    }

    @Override
    public void onTestExecuting(int currentTest, int totalTests) {
        SwingUtilities.invokeLater(() -> {
            int percent = (int) (((double) currentTest / totalTests) * 100);
            pbTestProgress.setValue(percent);
            pbTestProgress.setString(String.format("Executing Test %d / %d (%d%%)", currentTest, totalTests, percent));
        });
    }

    @Override
    public void onTestFinished(JudgeResultDTO result) {
        SwingUtilities.invokeLater(() -> {
            int percent = (int) (((double) result.getCurrentTestIndex() / result.getTotalTests()) * 100);
            pbTestProgress.setValue(percent);
            pbTestProgress.setString(String.format("Test %d/%d: %s (%d ms)",
                    result.getCurrentTestIndex(), result.getTotalTests(), result.getVerdict(), result.getExecutionTimeMs()));
        });
    }

    @Override
    public void onJobFinished(SubmissionDTO submission) {
        SwingUtilities.invokeLater(() -> {
            lblCurrentTask.setText(String.format("Sub #%d Completed -> %s (Time: %d ms)",
                    submission.getId(), submission.getVerdict(), submission.getExecutionTimeMs()));
            pbTestProgress.setValue(100);
            pbTestProgress.setString("Result: " + submission.getVerdict().name());
        });
    }

    @Override
    public void onSandboxConsoleOutput(String line) {
        SwingUtilities.invokeLater(() -> {
            txtConsole.append(line + "\n");
            txtConsole.setCaretPosition(txtConsole.getDocument().getLength());
        });
    }
}
