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
import javax.swing.JCheckBox;
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
 * Synchronized with Master modern FlatLightLaf design, Vietnamese labels,
 * Demo Slow Mode (400ms/test) toggle, and Abrupt Crash Simulation.
 */
public class WorkerMainFrame extends JFrame implements WorkerUiListener {

    private final WorkerBackendController controller;

    private final JTextField txtMasterHost = new JTextField("127.0.0.1", 14);
    private final JTextField txtMasterPort = new JTextField("1099", 6);
    private final JButton btnDiscover = new JButton("● Tự Tìm Master (UDP :8888)");
    private final JButton btnConnect = new JButton("► Kết Nối Cụm Master");
    private final JCheckBox chkSlowMode = new JCheckBox("♦ Chế Độ Demo Chậm (400ms / test case)", true);
    private final JButton btnCrash = new JButton("× Giả Lập Sập Máy Chấm (Kill Node)");

    private final JLabel lblWorkerIdChip = new JLabel();
    private final JLabel lblStatusChip = new JLabel("● OFFLINE (CHƯA KẾT NỐI)");
    private final JLabel lblCurrentTask = new JLabel("Đang chờ nhận bài nộp từ Master Server...");
    private final JProgressBar pbTestProgress = new JProgressBar(0, 100);
    private final JTextArea txtConsole = new JTextArea();

    public WorkerMainFrame(WorkerBackendController controller) {
        super("PulseOJ - Trạm Chấm Bài Phân Tán [" + controller.getWorkerId() + "]");
        this.controller = controller;

        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(980, 700);
        setMinimumSize(new Dimension(860, 540));
        setLocationRelativeTo(null);

        controller.setUiListener(this);
        chkSlowMode.setSelected(controller.isDemoSlowMode());

        setLayout(new BorderLayout(0, 0));

        // 1. Top Header Banner (Synchronized with MasterMainFrame)
        JPanel headerBanner = new JPanel(new MigLayout("insets 12 18 12 18, fillx", "[grow][]", "[]"));
        headerBanner.setBackground(Color.WHITE);
        headerBanner.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(226, 232, 240)));

        JPanel titleBlock = new JPanel(new MigLayout("insets 0, gap 2", "[grow]", "[][]"));
        titleBlock.setBackground(Color.WHITE);
        JLabel lblAppTitle = new JLabel("♦ PULSE OJ — JUDGE WORKER NODE");
        lblAppTitle.setFont(new Font("Segoe UI", Font.BOLD, 18));
        lblAppTitle.setForeground(new Color(15, 23, 42));
        lblAppTitle.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 12));

        JLabel lblAppSubtitle = new JLabel("Trạm Chấm Bài Phân Tán • Cô Lập Tiến Trình Đa Ngôn Ngữ (Java / C++ / Python) • UDP Heartbeat :8889");
        lblAppSubtitle.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        lblAppSubtitle.setForeground(new Color(100, 116, 139));
        lblAppSubtitle.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 12));

        titleBlock.add(lblAppTitle, "wrap");
        titleBlock.add(lblAppSubtitle);
        headerBanner.add(titleBlock, "align left");

        JPanel chipsPanel = new JPanel(new MigLayout("insets 0, gap 10", "[][]", "[]"));
        chipsPanel.setBackground(Color.WHITE);

        lblWorkerIdChip.setText("■ Node: " + controller.getWorkerId());
        lblWorkerIdChip.setOpaque(true);
        lblWorkerIdChip.setBackground(new Color(241, 245, 249));
        lblWorkerIdChip.setForeground(new Color(51, 65, 85));
        lblWorkerIdChip.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(203, 213, 225), 1),
                BorderFactory.createEmptyBorder(6, 12, 6, 12)
        ));
        lblWorkerIdChip.setFont(new Font("Segoe UI", Font.BOLD, 12));

        styleStatusChip(WorkerStatus.OFFLINE);

        chipsPanel.add(lblWorkerIdChip);
        chipsPanel.add(lblStatusChip);
        headerBanner.add(chipsPanel, "align right");

        add(headerBanner, BorderLayout.NORTH);

        // 2. Main Body Container
        JPanel bodyPanel = new JPanel(new BorderLayout(10, 10));
        bodyPanel.setBorder(BorderFactory.createEmptyBorder(12, 14, 12, 14));

        // 2a. Cluster Connection & Demo Controls Bar (2 balanced rows - zero clipping)
        JPanel topBar = new JPanel(new MigLayout("insets 10 12 10 12, gap 10, fillx", "[][grow][][][][]", "[][]"));
        topBar.setBorder(BorderFactory.createTitledBorder("Cấu Hình Kết Nối Cụm Master & Điều Khiển Kịch Bản Bảo Vệ"));

        topBar.add(new JLabel("Master IP:"));
        topBar.add(txtMasterHost, "w 150!, growx");
        topBar.add(new JLabel("Cổng RMI:"));
        topBar.add(txtMasterPort, "w 75!");

        btnDiscover.setFont(new Font("Segoe UI", Font.BOLD, 12));
        topBar.add(btnDiscover);

        btnConnect.setBackground(new Color(37, 99, 235));
        btnConnect.setForeground(Color.WHITE);
        btnConnect.setFont(new Font("Segoe UI", Font.BOLD, 12));
        topBar.add(btnConnect, "wrap");

        JPanel row2 = new JPanel(new MigLayout("insets 2 0 0 0, fillx", "[grow][]", "[]"));
        chkSlowMode.setFont(new Font("Segoe UI", Font.BOLD, 12));
        chkSlowMode.setForeground(new Color(180, 83, 9));
        chkSlowMode.setToolTipText("Nghỉ 400ms giữa mỗi test case để kịp quan sát tiến trình thực thi và thử nghiệm rút cáp/sập nguồn giữa chừng");
        row2.add(chkSlowMode, "align left");

        btnCrash.setBackground(new Color(220, 38, 38));
        btnCrash.setForeground(Color.WHITE);
        btnCrash.setFont(new Font("Segoe UI", Font.BOLD, 12));
        btnCrash.setEnabled(false);
        row2.add(btnCrash, "align right");
        topBar.add(row2, "span 6, growx");

        bodyPanel.add(topBar, BorderLayout.NORTH);

        // 2b. Center Panel (Active Judging Task + Sandbox Console)
        JPanel centerPanel = new JPanel(new BorderLayout(10, 10));

        JPanel progressHeader = new JPanel(new MigLayout("insets 10, gap 8, fillx", "[grow][]", "[]"));
        progressHeader.setBorder(BorderFactory.createTitledBorder("Tiến Trình Chấm Bài Hiện Tại (Live Sandbox Execution)"));

        lblCurrentTask.setFont(new Font("Segoe UI", Font.BOLD, 13));
        lblCurrentTask.setForeground(new Color(30, 41, 59));
        progressHeader.add(lblCurrentTask, "span 2, wrap");

        pbTestProgress.setStringPainted(true);
        pbTestProgress.setString("Chưa có tác vụ (Idle)");
        pbTestProgress.setPreferredSize(new Dimension(400, 26));
        progressHeader.add(pbTestProgress, "growx");

        JButton btnClearConsole = new JButton("× Xóa Nhật Ký");
        btnClearConsole.setBackground(new Color(241, 245, 249));
        btnClearConsole.setForeground(new Color(51, 65, 85));
        btnClearConsole.addActionListener(e -> txtConsole.setText(""));
        progressHeader.add(btnClearConsole, "align right");

        centerPanel.add(progressHeader, BorderLayout.NORTH);

        // Console output area
        txtConsole.setEditable(false);
        txtConsole.setFont(new Font("Consolas", Font.PLAIN, 13));
        txtConsole.setBackground(new Color(15, 23, 42));
        txtConsole.setForeground(new Color(226, 232, 240));
        JScrollPane consoleScroll = new JScrollPane(txtConsole);
        consoleScroll.setBorder(BorderFactory.createTitledBorder("Nhật Ký Thực Thi Hộp Cát (ProcessBuilder Sandbox Console Output)"));
        centerPanel.add(consoleScroll, BorderLayout.CENTER);

        bodyPanel.add(centerPanel, BorderLayout.CENTER);
        add(bodyPanel, BorderLayout.CENTER);

        // Event Listeners
        btnDiscover.addActionListener(e -> autoDiscoverMaster());
        btnConnect.addActionListener(e -> toggleConnection());
        chkSlowMode.addActionListener(e -> controller.setDemoSlowMode(chkSlowMode.isSelected()));
        btnCrash.addActionListener(e -> simulateCrash());
    }

    private void styleStatusChip(WorkerStatus status) {
        lblStatusChip.setOpaque(true);
        lblStatusChip.setFont(new Font("Segoe UI", Font.BOLD, 12));
        if (status == WorkerStatus.IDLE) {
            lblStatusChip.setText("● IDLE (SẴN SÀNG)");
            lblStatusChip.setBackground(new Color(236, 253, 245));
            lblStatusChip.setForeground(new Color(5, 150, 105));
            lblStatusChip.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(new Color(167, 243, 208), 1),
                    BorderFactory.createEmptyBorder(6, 12, 6, 12)
            ));
        } else if (status == WorkerStatus.BUSY) {
            lblStatusChip.setText("● BUSY (ĐANG CHẤM)");
            lblStatusChip.setBackground(new Color(239, 246, 255));
            lblStatusChip.setForeground(new Color(29, 78, 216));
            lblStatusChip.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(new Color(191, 219, 254), 1),
                    BorderFactory.createEmptyBorder(6, 12, 6, 12)
            ));
        } else {
            lblStatusChip.setText("● OFFLINE (NGẮT KẾT NỐI)");
            lblStatusChip.setBackground(new Color(254, 242, 242));
            lblStatusChip.setForeground(new Color(220, 38, 38));
            lblStatusChip.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(new Color(254, 202, 202), 1),
                    BorderFactory.createEmptyBorder(6, 12, 6, 12)
            ));
        }
    }

    private void autoDiscoverMaster() {
        btnDiscover.setEnabled(false);
        txtConsole.append(">>> Đang phát sóng UDP Broadcast tìm Master trên cổng 8888 (timeout 2000 ms)...\n");

        CompletableFuture.runAsync(() -> {
            UdpDiscoveryClient.DiscoveryResult result = controller.autoDiscoverMaster(2000);
            SwingUtilities.invokeLater(() -> {
                btnDiscover.setEnabled(true);
                if (result.found()) {
                    txtMasterHost.setText(result.masterHost());
                    txtMasterPort.setText(String.valueOf(result.masterRmiPort()));
                    txtConsole.append(">>> Đã tìm thấy Master Server tại: " + result.masterHost() + ":" + result.masterRmiPort() + "\n");
                } else {
                    txtConsole.append(">>> Không tìm thấy Master: " + result.message() + "\n");
                    JOptionPane.showMessageDialog(this, result.message(), "Tự Động Tìm Kiếm LAN", JOptionPane.INFORMATION_MESSAGE);
                }
            });
        });
    }

    private void toggleConnection() {
        if (!controller.isConnected()) {
            String host = txtMasterHost.getText().trim();
            int port = Integer.parseInt(txtMasterPort.getText().trim());

            btnConnect.setEnabled(false);
            txtConsole.append(">>> Đang đăng ký Worker với Master tại " + host + ":" + port + "...\n");

            CompletableFuture.runAsync(() -> {
                boolean ok = controller.connectToMaster(host, port);
                SwingUtilities.invokeLater(() -> {
                    btnConnect.setEnabled(true);
                    if (ok) {
                        btnConnect.setText("× Ngắt Kết Nối");
                        btnConnect.setBackground(new Color(100, 116, 139));
                        btnCrash.setEnabled(true);
                        txtMasterHost.setEnabled(false);
                        txtMasterPort.setEnabled(false);
                        btnDiscover.setEnabled(false);
                    } else {
                        JOptionPane.showMessageDialog(this,
                                "Không thể kết nối tới Master RMI Server tại " + host + ":" + port,
                                "Lỗi Kết Nối", JOptionPane.ERROR_MESSAGE);
                    }
                });
            });
        } else {
            controller.disconnect();
            btnConnect.setText("► Kết Nối Cụm Master");
            btnConnect.setBackground(new Color(37, 99, 235));
            btnCrash.setEnabled(false);
            txtMasterHost.setEnabled(true);
            txtMasterPort.setEnabled(true);
            btnDiscover.setEnabled(true);
            styleStatusChip(WorkerStatus.OFFLINE);
            lblCurrentTask.setText("Đã ngắt kết nối khỏi cụm Master.");
        }
    }

    private void simulateCrash() {
        int confirm = JOptionPane.showConfirmDialog(this,
                "Bạn có chắc chắn muốn giả lập sự cố sập nguồn / đứt cáp đột ngột (Kill Node)?\n" +
                "- Dừng ngay lập tức luồng phát nhịp tim UDP Heartbeat (:8889).\n" +
                "- Hủy đối tượng RMI Stub mà không gửi thông báo unregister.\n" +
                "- Master Watchdog sẽ phát hiện quá hạn (>6 giây) và tự động thu hồi bài đang chấm sang máy khác!",
                "Xác Nhận Giả Lập Sập Máy Chấm", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);

        if (confirm == JOptionPane.YES_OPTION) {
            controller.simulateCrash();
            btnConnect.setText("► Kết Nối Cụm Master");
            btnConnect.setBackground(new Color(37, 99, 235));
            btnCrash.setEnabled(false);
            txtMasterHost.setEnabled(true);
            txtMasterPort.setEnabled(true);
            btnDiscover.setEnabled(true);
            styleStatusChip(WorkerStatus.OFFLINE);
        }
    }

    // =========================================================================
    // WorkerUiListener Implementation (Dispatched safely on Swing EDT)
    // =========================================================================

    @Override
    public void onWorkerStatusChanged(WorkerStatus status) {
        SwingUtilities.invokeLater(() -> {
            styleStatusChip(status);
            if (status == WorkerStatus.IDLE) {
                pbTestProgress.setString("Sẵn sàng nhận bài (Idle)");
            } else if (status == WorkerStatus.OFFLINE) {
                pbTestProgress.setString("Ngoại tuyến (Offline)");
            }
        });
    }

    @Override
    public void onJobReceived(SubmissionDTO submission) {
        SwingUtilities.invokeLater(() -> {
            lblCurrentTask.setText(String.format("► Đang chấm Bài nộp #%d | Đề: %s | Ngôn ngữ: %s | Thí sinh: %s",
                    submission.getId(), submission.getProblemCode(), submission.getLanguage(), submission.getContestantUsername()));
            pbTestProgress.setValue(0);
            pbTestProgress.setString("Đang biên dịch mã nguồn (Compiling)...");
        });
    }

    @Override
    public void onTestExecuting(int currentTest, int totalTests) {
        SwingUtilities.invokeLater(() -> {
            int percent = (int) (((double) currentTest / totalTests) * 100);
            pbTestProgress.setValue(percent);
            pbTestProgress.setString(String.format("Đang chạy Test %d / %d (%d%%)", currentTest, totalTests, percent));
        });
    }

    @Override
    public void onTestFinished(JudgeResultDTO result) {
        SwingUtilities.invokeLater(() -> {
            int percent = (int) (((double) result.getCurrentTestIndex() / result.getTotalTests()) * 100);
            pbTestProgress.setValue(percent);
            pbTestProgress.setString(String.format("Test %d/%d -> %s (%d ms)",
                    result.getCurrentTestIndex(), result.getTotalTests(), result.getVerdict(), result.getExecutionTimeMs()));
        });
    }

    @Override
    public void onJobFinished(SubmissionDTO submission) {
        SwingUtilities.invokeLater(() -> {
            lblCurrentTask.setText(String.format("[OK] Hoàn tất Bài nộp #%d → Kết quả: %s (Thời gian max: %d ms)",
                    submission.getId(), submission.getVerdict(), submission.getExecutionTimeMs()));
            pbTestProgress.setValue(100);
            pbTestProgress.setString("Hoàn tất: " + submission.getVerdict().name());
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
