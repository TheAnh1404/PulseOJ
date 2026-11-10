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
        tabbedPane.setFont(new Font("Segoe UI", Font.BOLD, 13));
        tabbedPane.addTab("👥 Thí sinh (Neon DB)", contestantPanel);
        tabbedPane.addTab("📚 Đề bài & Test Cases", problemPanel);
        tabbedPane.addTab("⏱️ Hàng đợi Chấm bài (Live Queue)", queuePanel);
        tabbedPane.addTab("📡 Giám sát Cụm & UDP", clusterMonitorPanel);

        // Top Header Banner (Modern & Bright)
        JPanel headerBanner = new JPanel(new MigLayout("insets 12 18 12 18, fillx", "[grow][]", "[]"));
        headerBanner.setBackground(Color.WHITE);
        headerBanner.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(226, 232, 240)));

        JPanel titleBlock = new JPanel(new MigLayout("insets 0, gap 2", "[]", "[][]"));
        titleBlock.setBackground(Color.WHITE);
        JLabel lblAppTitle = new JLabel("⚡ PULSE OJ — MASTER SERVER");
        lblAppTitle.setFont(new Font("Segoe UI", Font.BOLD, 18));
        lblAppTitle.setForeground(new Color(15, 23, 42)); // Deep Slate

        JLabel lblAppSubtitle = new JLabel("Bảng Điều Khiển Giáo Viên • Neon Cloud Database • Hệ Thống Chấm Phân Tán");
        lblAppSubtitle.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        lblAppSubtitle.setForeground(new Color(100, 116, 139)); // Slate-500

        titleBlock.add(lblAppTitle, "wrap");
        titleBlock.add(lblAppSubtitle);
        headerBanner.add(titleBlock, "align left");

        // Status Chips Block
        JPanel chipsPanel = new JPanel(new MigLayout("insets 0, gap 10", "[][]", "[]"));
        chipsPanel.setBackground(Color.WHITE);

        lblServerStatus.setOpaque(true);
        lblServerStatus.setBackground(new Color(241, 245, 249));
        lblServerStatus.setForeground(new Color(51, 65, 85));
        lblServerStatus.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(203, 213, 225), 1),
                BorderFactory.createEmptyBorder(6, 12, 6, 12)
        ));
        lblServerStatus.setFont(new Font("Segoe UI", Font.BOLD, 12));

        lblDbStatus.setOpaque(true);
        lblDbStatus.setBackground(new Color(254, 243, 199));
        lblDbStatus.setForeground(new Color(146, 64, 14));
        lblDbStatus.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(253, 230, 138), 1),
                BorderFactory.createEmptyBorder(6, 12, 6, 12)
        ));
        lblDbStatus.setFont(new Font("Segoe UI", Font.BOLD, 12));

        chipsPanel.add(lblServerStatus);
        chipsPanel.add(lblDbStatus);
        headerBanner.add(chipsPanel, "align right");

        setLayout(new BorderLayout());
        add(headerBanner, BorderLayout.NORTH);
        add(tabbedPane, BorderLayout.CENTER);

        // Status Bar (South)
        JPanel statusBar = new JPanel(new MigLayout("insets 6 16 6 16, fillx", "[][grow]", "[]"));
        statusBar.setBackground(new Color(248, 250, 252));
        statusBar.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, new Color(226, 232, 240)));

        JLabel lblSystemTag = new JLabel("● TRẠNG THÁI HỆ THỐNG:");
        lblSystemTag.setFont(new Font("Segoe UI", Font.BOLD, 11));
        lblSystemTag.setForeground(new Color(100, 116, 139));
        statusBar.add(lblSystemTag);

        lblRecentEvent.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        lblRecentEvent.setForeground(new Color(30, 41, 59));
        statusBar.add(lblRecentEvent, "align left");

        add(statusBar, BorderLayout.SOUTH);

        // Start Master Server Backend in background
        startMasterBackend();
    }

    private void startMasterBackend() {
        new Thread(() -> {
            try {
                controller.startServer(1099, this, clusterMonitorPanel);

                SwingUtilities.invokeLater(() -> {
                    lblServerStatus.setText(String.format("🟢 Master RMI: %s:%d | UDP: 8888/8889",
                            controller.getServerIp(), controller.getRmiPort()));
                    lblServerStatus.setBackground(new Color(236, 253, 245));
                    lblServerStatus.setForeground(new Color(5, 150, 105));
                    lblServerStatus.setBorder(BorderFactory.createCompoundBorder(
                            BorderFactory.createLineBorder(new Color(167, 243, 208), 1),
                            BorderFactory.createEmptyBorder(6, 12, 6, 12)
                    ));

                    if (NeonDataSourceConfig.isMockMode()) {
                        lblDbStatus.setText("⚠️ Neon DB: MOCK MODE");
                        lblDbStatus.setBackground(new Color(254, 243, 199));
                        lblDbStatus.setForeground(new Color(180, 83, 9));
                        lblDbStatus.setBorder(BorderFactory.createCompoundBorder(
                                BorderFactory.createLineBorder(new Color(253, 230, 138), 1),
                                BorderFactory.createEmptyBorder(6, 12, 6, 12)
                        ));
                    } else {
                        lblDbStatus.setText("☁️ Neon DB: CONNECTED SSL");
                        lblDbStatus.setBackground(new Color(239, 246, 255));
                        lblDbStatus.setForeground(new Color(29, 78, 216));
                        lblDbStatus.setBorder(BorderFactory.createCompoundBorder(
                                BorderFactory.createLineBorder(new Color(191, 219, 254), 1),
                                BorderFactory.createEmptyBorder(6, 12, 6, 12)
                        ));
                    }
                });
            } catch (Exception e) {
                SwingUtilities.invokeLater(() -> {
                    lblServerStatus.setText("🔴 Server Start Failed: " + e.getMessage());
                    lblServerStatus.setBackground(new Color(254, 242, 242));
                    lblServerStatus.setForeground(new Color(220, 38, 38));
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
