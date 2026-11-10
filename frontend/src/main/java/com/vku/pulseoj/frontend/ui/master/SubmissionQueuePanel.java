package com.vku.pulseoj.frontend.ui.master;

import com.vku.pulseoj.backend.master.MasterBackendController;
import com.vku.pulseoj.common.dto.JudgeResultDTO;
import com.vku.pulseoj.common.dto.SubmissionDTO;
import com.vku.pulseoj.common.enums.Verdict;
import net.miginfocom.swing.MigLayout;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.SwingUtilities;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * [TV1] Master Live Submission Queue & Dispatch Monitor Panel.
 */
public class SubmissionQueuePanel extends JPanel {

    private final MasterBackendController controller;
    private final DefaultTableModel tableModel;
    private final JTable table;
    private final JLabel lblStats = new JLabel("Submissions in DB: 0 | Queue: 0");
    private final SimpleDateFormat timeFmt = new SimpleDateFormat("HH:mm:ss");

    public SubmissionQueuePanel(MasterBackendController controller) {
        this.controller = controller;
        setLayout(new BorderLayout(10, 10));
        setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // Top Stats bar
        JPanel topBar = new JPanel(new MigLayout("insets 5, gap 10"));
        lblStats.setFont(lblStats.getFont().deriveFont(Font.BOLD, 13f));
        topBar.add(lblStats, "growx");

        JButton btnRefresh = new JButton("Refresh Submissions");
        btnRefresh.addActionListener(e -> refreshSubmissionsAsync());
        topBar.add(btnRefresh, "align right");
        add(topBar, BorderLayout.NORTH);

        // Submissions Table
        String[] cols = {"ID", "Contestant", "Problem", "Lang", "Verdict", "Time (ms)", "Worker Node", "Submitted At"};
        tableModel = new DefaultTableModel(cols, 0) {
            @Override
            public boolean isCellEditable(int r, int c) { return false; }
        };
        table = new JTable(tableModel);
        table.setRowHeight(26);

        // Custom Cell Renderer for Verdict column (index 4)
        table.getColumnModel().getColumn(4).setCellRenderer(new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
                Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
                if (value != null) {
                    String str = value.toString();
                    if (str.startsWith("AC") || str.contains("Accepted")) {
                        c.setForeground(new Color(40, 180, 99)); // Green
                        c.setFont(c.getFont().deriveFont(Font.BOLD));
                    } else if (str.startsWith("WA") || str.startsWith("RE") || str.startsWith("TLE") || str.startsWith("CE")) {
                        c.setForeground(new Color(231, 76, 60)); // Red
                        c.setFont(c.getFont().deriveFont(Font.BOLD));
                    } else if (str.startsWith("RUNNING")) {
                        c.setForeground(new Color(52, 152, 219)); // Blue
                        c.setFont(c.getFont().deriveFont(Font.BOLD));
                    } else {
                        c.setForeground(new Color(243, 156, 18)); // Yellow/Orange
                    }
                }
                return c;
            }
        });

        add(new JScrollPane(table), BorderLayout.CENTER);

        refreshSubmissionsAsync();
    }

    public void refreshSubmissionsAsync() {
        CompletableFuture.runAsync(() -> {
            List<SubmissionDTO> list = controller.getAllSubmissions();
            int qSize = (controller.getDispatcher() != null) ? controller.getDispatcher().getQueueSize() : 0;
            SwingUtilities.invokeLater(() -> {
                tableModel.setRowCount(0);
                for (SubmissionDTO s : list) {
                    tableModel.addRow(new Object[]{
                            s.getId(),
                            s.getContestantUsername(),
                            s.getProblemCode(),
                            s.getLanguage() != null ? s.getLanguage().name() : "N/A",
                            s.getVerdict() != null ? s.getVerdict().name() : "PENDING",
                            s.getExecutionTimeMs(),
                            s.getWorkerId() != null ? s.getWorkerId() : "---",
                            s.getSubmittedAt() > 0 ? timeFmt.format(new Date(s.getSubmittedAt())) : "N/A"
                    });
                }
                lblStats.setText("Submissions in DB: " + list.size() + " | In-Flight/Queue: " + qSize);
            });
        });
    }

    public void updateSubmissionProgress(JudgeResultDTO result) {
        SwingUtilities.invokeLater(() -> {
            for (int r = 0; r < tableModel.getRowCount(); r++) {
                int id = (int) tableModel.getValueAt(r, 0);
                if (id == result.getSubmissionId()) {
                    String progressStr = "RUNNING (" + result.getCurrentTestIndex() + "/" + result.getTotalTests() + ")";
                    tableModel.setValueAt(progressStr, r, 4);
                    tableModel.setValueAt(result.getExecutionTimeMs(), r, 5);
                    tableModel.setValueAt(result.getWorkerId(), r, 6);
                    break;
                }
            }
        });
    }

    public void updateSubmissionFinal(SubmissionDTO sub) {
        SwingUtilities.invokeLater(() -> {
            boolean found = false;
            for (int r = 0; r < tableModel.getRowCount(); r++) {
                int id = (int) tableModel.getValueAt(r, 0);
                if (id == sub.getId()) {
                    tableModel.setValueAt(sub.getVerdict().name(), r, 4);
                    tableModel.setValueAt(sub.getExecutionTimeMs(), r, 5);
                    tableModel.setValueAt(sub.getWorkerId(), r, 6);
                    found = true;
                    break;
                }
            }
            if (!found) {
                refreshSubmissionsAsync();
            }
        });
    }
}
