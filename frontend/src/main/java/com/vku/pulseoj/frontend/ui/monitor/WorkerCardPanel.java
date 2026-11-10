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
        setLayout(new MigLayout("insets 10, gap 5, fillx", "[grow]", "[]"));
        setPreferredSize(new Dimension(240, 200));
        setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(60, 63, 65), 1),
                BorderFactory.createEmptyBorder(8, 8, 8, 8)
        ));

        lblTitle.setText(workerId);
        lblTitle.setFont(lblTitle.getFont().deriveFont(Font.BOLD, 14f));
        add(lblTitle, "wrap");

        lblHostPort.setFont(lblHostPort.getFont().deriveFont(11f));
        lblHostPort.setForeground(Color.GRAY);
        add(lblHostPort, "wrap");

        lblStatus.setFont(lblStatus.getFont().deriveFont(Font.BOLD, 12f));
        add(lblStatus, "wrap, gapbottom 5");

        // CPU Bar
        add(new JLabel("CPU Usage:"), "wrap");
        pbCpu.setStringPainted(true);
        add(pbCpu, "growx, wrap");

        // RAM Bar
        add(new JLabel("RAM Usage:"), "wrap");
        pbRam.setStringPainted(true);
        add(pbRam, "growx, wrap");

        // Jobs Info
        lblJobs.setFont(lblJobs.getFont().deriveFont(11f));
        add(lblJobs, "wrap, gaptop 5");
    }

    public void updateMetrics(WorkerMetricsDTO metrics) {
        lblHostPort.setText(metrics.getWorkerHost() + ":" + metrics.getWorkerRmiPort());

        WorkerStatus status = metrics.getStatus();
        lblStatus.setText(status.name());
        if (status == WorkerStatus.IDLE) {
            lblStatus.setForeground(new Color(46, 204, 113)); // Green
        } else if (status == WorkerStatus.BUSY) {
            lblStatus.setForeground(new Color(52, 152, 219)); // Blue
        } else {
            lblStatus.setForeground(new Color(231, 76, 60)); // Red
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
