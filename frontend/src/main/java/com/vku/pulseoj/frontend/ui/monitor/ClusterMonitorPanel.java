package com.vku.pulseoj.frontend.ui.monitor;

import com.vku.pulseoj.backend.discovery.ClusterMonitorListener;
import com.vku.pulseoj.backend.master.MasterBackendController;
import com.vku.pulseoj.common.dto.NetworkPacketLogDTO;
import com.vku.pulseoj.common.dto.ProblemDTO;
import com.vku.pulseoj.common.dto.SubmissionDTO;
import com.vku.pulseoj.common.dto.WorkerMetricsDTO;
import com.vku.pulseoj.common.enums.Language;
import net.miginfocom.swing.MigLayout;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.Font;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * [TV4] Complete Cluster Health Monitor, Stress Test Simulator & UDP Telemetry Dashboard.
 */
public class ClusterMonitorPanel extends JPanel implements ClusterMonitorListener {

    private final MasterBackendController controller;
    private final Map<String, WorkerCardPanel> workerCards = new ConcurrentHashMap<>();
    private final JPanel cardsContainer = new JPanel(new FlowLayout(FlowLayout.LEFT, 15, 15));
    private final NetworkInspectorPanel inspectorPanel = new NetworkInspectorPanel();

    private final JLabel lblClusterStats = new JLabel("Registered Workers: 0 | Online: 0 | Busy: 0 | Offline: 0");
    private final JButton btnStressTest = new JButton("Run Stress Test (20 Concurrent Submissions)");

    public ClusterMonitorPanel(MasterBackendController controller) {
        this.controller = controller;
        setLayout(new BorderLayout(10, 10));
        setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // 1. Top Header & Stress Test bar
        JPanel headerPanel = new JPanel(new MigLayout("insets 10, gap 15", "[grow][]", "[]"));
        lblClusterStats.setFont(lblClusterStats.getFont().deriveFont(Font.BOLD, 14f));
        headerPanel.add(lblClusterStats, "align left");

        btnStressTest.setFont(btnStressTest.getFont().deriveFont(Font.BOLD));
        btnStressTest.setBackground(new Color(155, 89, 182));
        btnStressTest.setForeground(Color.WHITE);
        btnStressTest.addActionListener(e -> runDistributedStressTest());
        headerPanel.add(btnStressTest, "align right");

        add(headerPanel, BorderLayout.NORTH);

        // 2. Center: Split Pane (Top: Worker Cards; Bottom: Network Inspector)
        JScrollPane cardsScroll = new JScrollPane(cardsContainer);
        cardsScroll.setBorder(BorderFactory.createTitledBorder("Live Worker Health Cards (UDP Telemetry 8889)"));

        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, cardsScroll, inspectorPanel);
        split.setResizeWeight(0.45);
        add(split, BorderLayout.CENTER);
    }

    @Override
    public void onWorkerMetricsReceived(WorkerMetricsDTO metrics) {
        SwingUtilities.invokeLater(() -> {
            WorkerCardPanel card = workerCards.computeIfAbsent(metrics.getWorkerId(), id -> {
                WorkerCardPanel newCard = new WorkerCardPanel(id);
                cardsContainer.add(newCard);
                cardsContainer.revalidate();
                cardsContainer.repaint();
                return newCard;
            });

            card.updateMetrics(metrics);
            updateClusterStatsHeader();
        });
    }

    @Override
    public void onWorkerTimeout(String workerId) {
        SwingUtilities.invokeLater(() -> {
            WorkerCardPanel card = workerCards.get(workerId);
            if (card != null) {
                card.setOffline();
            }
            updateClusterStatsHeader();
        });
    }

    @Override
    public void onPacketLogged(NetworkPacketLogDTO packetLog) {
        inspectorPanel.logPacket(packetLog);
    }

    private void updateClusterStatsHeader() {
        int total = workerCards.size();
        int busy = 0;
        int online = 0;
        int offline = 0;

        for (WorkerMetricsDTO m : controller.getHeartbeatWatchdog().getLatestMetrics().values()) {
            switch (m.getStatus()) {
                case IDLE -> online++;
                case BUSY -> { online++; busy++; }
                case OFFLINE -> offline++;
            }
        }

        lblClusterStats.setText(String.format("Registered Workers: %d | Online: %d (Busy: %d) | Offline: %d",
                total, online, busy, offline));
    }

    private void runDistributedStressTest() {
        List<ProblemDTO> problems = controller.getAllProblems();
        if (problems.isEmpty()) {
            JOptionPane.showMessageDialog(this, "No problems configured in DB to run stress test.", "Note", JOptionPane.WARNING_MESSAGE);
            return;
        }

        ProblemDTO targetProblem = problems.get(0);
        int submissionCount = 20;

        btnStressTest.setEnabled(false);
        CompletableFuture.runAsync(() -> {
            String sampleJava = """
                    import java.util.Scanner;
                    public class Main {
                        public static void main(String[] args) {
                            Scanner sc = new Scanner(System.in);
                            if (sc.hasNextLong()) {
                                long a = sc.nextLong();
                                long b = sc.nextLong();
                                System.out.println(a + b);
                            }
                        }
                    }
                    """;

            for (int i = 1; i <= submissionCount; i++) {
                try {
                    SubmissionDTO sub = new SubmissionDTO(
                            null,
                            1, // student01
                            "student01",
                            targetProblem.getId(),
                            targetProblem.getCode(),
                            Language.JAVA,
                            sampleJava
                    );
                    controller.getDispatcher().enqueueSubmission(sub);
                    Thread.sleep(80); // Small interval
                } catch (Exception ignored) {}
            }

            SwingUtilities.invokeLater(() -> {
                btnStressTest.setEnabled(true);
                JOptionPane.showMessageDialog(this,
                        "Injected 20 concurrent submissions into Master Queue!\nWatch the workers distribute and execute the load.",
                        "Stress Test Initiated", JOptionPane.INFORMATION_MESSAGE);
            });
        });
    }

    public NetworkInspectorPanel getInspectorPanel() {
        return inspectorPanel;
    }
}
