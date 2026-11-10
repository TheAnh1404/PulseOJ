package com.vku.pulseoj.frontend.ui.master;

import com.vku.pulseoj.backend.db.NeonDataSourceConfig;
import com.vku.pulseoj.backend.master.MasterBackendController;
import com.vku.pulseoj.backend.master.MasterUiListener;
import com.vku.pulseoj.common.dto.ContestantDTO;
import com.vku.pulseoj.common.dto.JudgeResultDTO;
import com.vku.pulseoj.common.dto.SubmissionDTO;
import com.vku.pulseoj.frontend.ui.monitor.ClusterMonitorPanel;
import net.miginfocom.swing.MigLayout;

import javax.swing.BorderFactory;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;

/**
 * [TV1 & TV4] Master Server Main Application Window (Teacher's Control Console).
 */
public class MasterMainFrame extends JFrame implements MasterUiListener {

    private final MasterBackendController controller;

    private final ContestantManagementPanel contestantPanel;
    private final ProblemManagementPanel problemPanel;
    private final SubmissionQueuePanel queuePanel;
    private final ClusterMonitorPanel clusterMonitorPanel;

    private final JLabel lblServerStatus = new JLabel("Status: Initializing...");
    private final JLabel lblDbStatus = new JLabel("DB: Checking...");
    private final JLabel lblRecentEvent = new JLabel("System Ready.");

    public MasterMainFrame(MasterBackendController controller) {
        super("PulseOJ - Master Server & Distributed Cluster Controller");
        this.controller = controller;

        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1180, 780);
        setMinimumSize(new Dimension(950, 600));
        setLocationRelativeTo(null);

        // Instantiate Panels
        contestantPanel = new ContestantManagementPanel(controller);
        problemPanel = new ProblemManagementPanel(controller);
        queuePanel = new SubmissionQueuePanel(controller);
        clusterMonitorPanel = new ClusterMonitorPanel(controller);

        // Main Tabbed Interface
        JTabbedPane tabbedPane = new JTabbedPane();
        tabbedPane.setFont(tabbedPane.getFont().deriveFont(Font.BOLD, 13f));
        tabbedPane.addTab("[TV1] Contestant Accounts (Neon DB)", contestantPanel);
        tabbedPane.addTab("[TV1] Problems & Test Cases", problemPanel);
        tabbedPane.addTab("[TV1] Live Submission Queue", queuePanel);
        tabbedPane.addTab("[TV4] Cluster Health & UDP Inspector", clusterMonitorPanel);

        setLayout(new BorderLayout());
        add(tabbedPane, BorderLayout.CENTER);

        // Status Bar (South)
        JPanel statusBar = new JPanel(new MigLayout("insets 5 10 5 10, fillx", "[][][grow]", "[]"));
        statusBar.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, new Color(50, 50, 50)));

        lblServerStatus.setFont(lblServerStatus.getFont().deriveFont(Font.BOLD, 12f));
        statusBar.add(lblServerStatus);
        statusBar.add(lblDbStatus);
        lblRecentEvent.setForeground(Color.GRAY);
        statusBar.add(lblRecentEvent, "align right");

        add(statusBar, BorderLayout.SOUTH);

        // Start Master Server Backend in background
        startMasterBackend();
    }

    private void startMasterBackend() {
        new Thread(() -> {
            try {
                controller.startServer(1099, this, clusterMonitorPanel);

                SwingUtilities.invokeLater(() -> {
                    lblServerStatus.setText(String.format("Master RMI: %s:%d | UDP Discovery: 8888 | Heartbeat: 8889",
                            controller.getServerIp(), controller.getRmiPort()));
                    lblServerStatus.setForeground(new Color(46, 204, 113));

                    if (NeonDataSourceConfig.isMockMode()) {
                        lblDbStatus.setText("[Neon DB: OFFLINE MOCK MODE - Seed data loaded]");
                        lblDbStatus.setForeground(new Color(243, 156, 18));
                    } else {
                        lblDbStatus.setText("[Neon DB: CONNECTED SSL]");
                        lblDbStatus.setForeground(new Color(46, 204, 113));
                    }
                });
            } catch (Exception e) {
                SwingUtilities.invokeLater(() -> {
                    lblServerStatus.setText("Server Start Failed: " + e.getMessage());
                    lblServerStatus.setForeground(new Color(231, 76, 60));
                    JOptionPane.showMessageDialog(this,
                            "Failed to start Master Server: " + e.getMessage(),
                            "Server Error", JOptionPane.ERROR_MESSAGE);
                });
            }
        }, "MasterStartup-Thread").start();
    }

    // =========================================================================
    // MasterUiListener Callbacks (All updates routed to Swing EDT)
    // =========================================================================

    @Override
    public void onSubmissionQueued(SubmissionDTO submission) {
        SwingUtilities.invokeLater(() -> {
            lblRecentEvent.setText("Queued sub #" + submission.getId() + " (" + submission.getProblemCode() + ")");
            queuePanel.refreshSubmissionsAsync();
        });
    }

    @Override
    public void onSubmissionDispatched(int submissionId, String workerId) {
        SwingUtilities.invokeLater(() -> {
            lblRecentEvent.setText("Dispatched sub #" + submissionId + " -> [" + workerId + "]");
            queuePanel.refreshSubmissionsAsync();
        });
    }

    @Override
    public void onTestProgressUpdate(JudgeResultDTO result) {
        SwingUtilities.invokeLater(() -> {
            queuePanel.updateSubmissionProgress(result);
            lblRecentEvent.setText(String.format("Sub #%d Test %d/%d: %s",
                    result.getSubmissionId(), result.getCurrentTestIndex(), result.getTotalTests(), result.getVerdict()));
        });
    }

    @Override
    public void onSubmissionCompleted(SubmissionDTO submission) {
        SwingUtilities.invokeLater(() -> {
            queuePanel.updateSubmissionFinal(submission);
            lblRecentEvent.setText(String.format("Sub #%d Completed: %s (%d ms)",
                    submission.getId(), submission.getVerdict(), submission.getExecutionTimeMs()));
        });
    }

    @Override
    public void onContestantUpdated(ContestantDTO contestant) {
        SwingUtilities.invokeLater(contestantPanel::refreshTableAsync);
    }

    @Override
    public void onServerLog(String message) {
        SwingUtilities.invokeLater(() -> lblRecentEvent.setText(message));
    }
}
