package com.vku.pulseoj.frontend.ui.monitor;

import com.vku.pulseoj.backend.discovery.ClusterMonitorListener;
import com.vku.pulseoj.backend.master.MasterBackendController;
import com.vku.pulseoj.common.dto.ContestantDTO;
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
 * Synchronized with Master modern FlatLightLaf design and 100% Vietnamese UI.
 */
public class ClusterMonitorPanel extends JPanel implements ClusterMonitorListener {

    private final MasterBackendController controller;
    private final Map<String, WorkerCardPanel> workerCards = new ConcurrentHashMap<>();
    private final JPanel cardsContainer = new JPanel(new FlowLayout(FlowLayout.LEFT, 15, 15));
    private final NetworkInspectorPanel inspectorPanel = new NetworkInspectorPanel();

    private final JLabel lblClusterStats = new JLabel("Tổng Máy Chấm: 0  |  ● Trực Tuyến: 0 (Đang Bận: 0)  |  × Mất Kết Nối: 0");
    private final JButton btnStressTest = new JButton("► Thử Nghiệm Bắn Tải (20 Bài Nộp Đồng Thời)");

    public ClusterMonitorPanel(MasterBackendController controller) {
        this.controller = controller;
        setLayout(new BorderLayout(10, 10));
        setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));

        cardsContainer.setBackground(new Color(248, 250, 252));

        // 1. Top Header & Stress Test bar
        JPanel headerPanel = new JPanel(new MigLayout("insets 4 8 8 8, gap 15, fillx", "[grow][]", "[]"));
        lblClusterStats.setFont(new Font("Segoe UI", Font.BOLD, 13));
        lblClusterStats.setForeground(new Color(30, 41, 59));
        headerPanel.add(lblClusterStats, "align left");

        btnStressTest.setFont(new Font("Segoe UI", Font.BOLD, 12));
        btnStressTest.setBackground(new Color(124, 58, 237)); // Purple-600
        btnStressTest.setForeground(Color.WHITE);
        btnStressTest.setToolTipText("Giả lập 20 thí sinh nộp bài cùng lúc để kiểm chứng điều phối tải (Load Balancing) giữa các Worker");
        btnStressTest.addActionListener(e -> runDistributedStressTest());
        headerPanel.add(btnStressTest, "align right");

        add(headerPanel, BorderLayout.NORTH);

        // 2. Center: Split Pane (Top: Worker Cards; Bottom: Network Inspector)
        JScrollPane cardsScroll = new JScrollPane(cardsContainer);
        cardsScroll.setBorder(BorderFactory.createTitledBorder("Thẻ Trạng Thái Cụm Máy Chấm Thời Gian Thực (UDP Telemetry :8889 — Ngưỡng Timeout: 6s)"));
        cardsScroll.getViewport().setBackground(new Color(248, 250, 252));

        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, cardsScroll, inspectorPanel);
        split.setDividerLocation(245);
        split.setResizeWeight(0.48);
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

        lblClusterStats.setText(String.format(
                "Tổng Máy Chấm: %d  |  ● Trực Tuyến: %d (Đang Chấm: %d)  |  × Mất Kết Nối: %d",
                total, online, busy, offline
        ));
    }

    private void runDistributedStressTest() {
        List<ProblemDTO> problems = controller.getAllProblems();
        if (problems.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                    "Chưa có đề bài nào trong CSDL để chạy thử nghiệm bắn tải.",
                    "Thông Báo", JOptionPane.WARNING_MESSAGE);
            return;
        }

        ProblemDTO targetProblem = problems.get(0);
        List<ContestantDTO> activeContestants = controller.getAllContestants().stream()
                .filter(c -> !c.isLocked())
                .toList();
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
                    ContestantDTO actor = activeContestants.isEmpty()
                            ? null
                            : activeContestants.get((i - 1) % activeContestants.size());
                    int contestantId = (actor != null) ? actor.getId() : 101;
                    String username = (actor != null) ? actor.getUsername() : "student01";
                    SubmissionDTO sub = new SubmissionDTO(
                            null,
                            contestantId,
                            username,
                            targetProblem.getId(),
                            targetProblem.getCode(),
                            Language.JAVA,
                            sampleJava
                    );
                    controller.submitLocalSubmission(sub);
                    Thread.sleep(80);
                } catch (Exception ignored) {}
            }

            SwingUtilities.invokeLater(() -> {
                btnStressTest.setEnabled(true);
                JOptionPane.showMessageDialog(this,
                        "Đã bơm đồng thời 20 bài nộp vào Hàng Đợi Master (Đề: " + targetProblem.getCode() + ")!\n" +
                        "Hãy quan sát các máy chấm Worker chia tải thực thi và theo dõi tab Hàng Đợi Chấm Bài.",
                        "Đã Kích Hoạt Bắn Tải Phân Tán", JOptionPane.INFORMATION_MESSAGE);
            });
        });
    }

    public NetworkInspectorPanel getInspectorPanel() {
        return inspectorPanel;
    }
}
