package com.vku.pulseoj.frontend.ui.monitor;

import com.vku.pulseoj.common.dto.WorkerMetricsDTO;
import com.vku.pulseoj.common.enums.WorkerStatus;
import net.miginfocom.swing.MigLayout;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;

/**
 * [TV4] Visual Card displaying CPU, RAM, and health status for an individual Worker node.
 * Synchronized with Master modern FlatLightLaf design and 100% Vietnamese UI.
 */
public class WorkerCardPanel extends JPanel {

    private final String workerId;
    private final JLabel lblTitle = new JLabel();
    private final JLabel lblHostPort = new JLabel();
    private final JLabel lblStatus = new JLabel();
    private final JProgressBar pbCpu = new JProgressBar(0, 100);
    private final JProgressBar pbRam = new JProgressBar(0, 100);
    private final JLabel lblJobs = new JLabel("Đang chấm: 0 | Hoàn thành: 0");

    public WorkerCardPanel(String workerId) {
        this.workerId = workerId;
        setLayout(new MigLayout("insets 8 10 8 10, gap 4, fillx", "[grow]", "[]"));
        setPreferredSize(new Dimension(280, 195));
        setBackground(Color.WHITE);
        applyCardBorder(new Color(226, 232, 240));

        lblTitle.setText("♦ " + workerId + " ");
        lblTitle.setFont(new Font("Segoe UI", Font.BOLD, 14));
        lblTitle.setForeground(new Color(15, 23, 42));
        add(lblTitle, "growx, wrap");

        lblHostPort.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        lblHostPort.setForeground(new Color(100, 116, 139));
        add(lblHostPort, "growx, wrap");

        lblStatus.setFont(new Font("Segoe UI", Font.BOLD, 12));
        add(lblStatus, "growx, wrap, gapbottom 2");

        // CPU Bar
        JLabel lblCpuTag = new JLabel("Tải CPU (CPU Usage):");
        lblCpuTag.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        lblCpuTag.setForeground(new Color(71, 85, 105));
        add(lblCpuTag, "wrap");
        pbCpu.setStringPainted(true);
        add(pbCpu, "growx, wrap");

        // RAM Bar
        JLabel lblRamTag = new JLabel("Bộ Nhớ RAM (Memory):");
        lblRamTag.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        lblRamTag.setForeground(new Color(71, 85, 105));
        add(lblRamTag, "wrap");
        pbRam.setStringPainted(true);
        add(pbRam, "growx, wrap");

        // Jobs Info
        lblJobs.setFont(new Font("Segoe UI", Font.BOLD, 11));
        lblJobs.setForeground(new Color(71, 85, 105));
        add(lblJobs, "growx, wrap, gaptop 2");
    }

    private void applyCardBorder(Color borderColor) {
        setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(borderColor, 1),
                BorderFactory.createEmptyBorder(6, 8, 6, 8)
        ));
    }

    public void updateMetrics(WorkerMetricsDTO metrics) {
        lblHostPort.setText("IP: " + metrics.getWorkerHost() + " | Cổng RMI: " + metrics.getWorkerRmiPort());

        WorkerStatus status = metrics.getStatus();
        if (status == WorkerStatus.IDLE) {
            lblStatus.setText("● Trạng thái: IDLE (Sẵn sàng)");
            lblStatus.setForeground(new Color(22, 163, 74));
            applyCardBorder(new Color(167, 243, 208));
        } else if (status == WorkerStatus.BUSY) {
            lblStatus.setText("● Trạng thái: BUSY (Đang chấm)");
            lblStatus.setForeground(new Color(37, 99, 235));
            applyCardBorder(new Color(147, 197, 253));
        } else {
            lblStatus.setText("× Trạng thái: OFFLINE");
            lblStatus.setForeground(new Color(220, 38, 38));
            applyCardBorder(new Color(252, 165, 165));
        }

        int cpu = (int) metrics.getCpuLoadPercent();
        pbCpu.setValue(cpu);
        pbCpu.setString(cpu + "%");

        int ramPercent = (int) metrics.getRamUsagePercent();
        pbRam.setValue(ramPercent);
        pbRam.setString(metrics.getUsedRamMb() + " MB / " + metrics.getTotalRamMb() + " MB (" + ramPercent + "%)");

        lblJobs.setText(String.format("Đang chấm: %d  |  Đã hoàn tất: %d bài",
                metrics.getActiveJobs(), metrics.getCompletedJobs()));
    }

    public void setOffline() {
        lblStatus.setText("× MẤT KẾT NỐI (TIMEOUT >6s)");
        lblStatus.setForeground(new Color(220, 38, 38));
        applyCardBorder(new Color(252, 165, 165));
        pbCpu.setValue(0);
        pbCpu.setString("Mất tín hiệu");
        pbRam.setValue(0);
        pbRam.setString("Mất tín hiệu");
    }

    public String getWorkerId() {
        return workerId;
    }
}
