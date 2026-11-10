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
 */
public class WorkerCardPanel extends JPanel {

    private final String workerId;
    private final JLabel lblTitle = new JLabel();
    private final JLabel lblHostPort = new JLabel();
    private final JLabel lblStatus = new JLabel();
    private final JProgressBar pbCpu = new JProgressBar(0, 100);
    private final JProgressBar pbRam = new JProgressBar(0, 100);
    private final JLabel lblJobs = new JLabel("Jobs: Active: 0 | Done: 0");

    public WorkerCardPanel(String workerId) {
        this.workerId = workerId;
        setLayout(new MigLayout("insets 12, gap 6, fillx", "[grow]", "[]"));
        setPreferredSize(new Dimension(250, 210));
        setBackground(Color.WHITE);
        setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(226, 232, 240), 1),
                BorderFactory.createEmptyBorder(10, 10, 10, 10)
        ));

        lblTitle.setText("🖥️ " + workerId);
        lblTitle.setFont(new Font("Segoe UI", Font.BOLD, 14));
        lblTitle.setForeground(new Color(15, 23, 42));
        add(lblTitle, "wrap");

        lblHostPort.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        lblHostPort.setForeground(new Color(100, 116, 139));
        add(lblHostPort, "wrap");

        lblStatus.setFont(new Font("Segoe UI", Font.BOLD, 12));
        add(lblStatus, "wrap, gapbottom 5");

        // CPU Bar
        JLabel lblCpuTag = new JLabel("CPU Usage:");
        lblCpuTag.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        lblCpuTag.setForeground(new Color(71, 85, 105));
        add(lblCpuTag, "wrap");
        pbCpu.setStringPainted(true);
        add(pbCpu, "growx, wrap");

        // RAM Bar
        JLabel lblRamTag = new JLabel("RAM Usage:");
        lblRamTag.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        lblRamTag.setForeground(new Color(71, 85, 105));
        add(lblRamTag, "wrap");
        pbRam.setStringPainted(true);
        add(pbRam, "growx, wrap");

        // Jobs Info
        lblJobs.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        lblJobs.setForeground(new Color(100, 116, 139));
        add(lblJobs, "wrap, gaptop 5");
    }

    public void updateMetrics(WorkerMetricsDTO metrics) {
        lblHostPort.setText("IP: " + metrics.getWorkerHost() + " | Port: " + metrics.getWorkerRmiPort());

        WorkerStatus status = metrics.getStatus();
        lblStatus.setText("Trạng thái: " + status.name());
        if (status == WorkerStatus.IDLE) {
            lblStatus.setForeground(new Color(22, 163, 74)); // Green
        } else if (status == WorkerStatus.BUSY) {
            lblStatus.setForeground(new Color(37, 99, 235)); // Blue
        } else {
            lblStatus.setForeground(new Color(220, 38, 38)); // Red
        }

        int cpu = (int) metrics.getCpuLoadPercent();
        pbCpu.setValue(cpu);
        pbCpu.setString(cpu + "%");

        int ramPercent = (int) metrics.getRamUsagePercent();
        pbRam.setValue(ramPercent);
        pbRam.setString(metrics.getUsedRamMb() + "MB / " + metrics.getTotalRamMb() + "MB (" + ramPercent + "%)");

        lblJobs.setText(String.format("Active: %d | Finished: %d", metrics.getActiveJobs(), metrics.getCompletedJobs()));
    }

    public void setOffline() {
        lblStatus.setText("OFFLINE (TIMEOUT >6s)");
        lblStatus.setForeground(new Color(231, 76, 60));
        pbCpu.setValue(0);
        pbCpu.setString("Offline");
        pbRam.setValue(0);
        pbRam.setString("Offline");
    }

    public String getWorkerId() {
        return workerId;
    }
}
