package com.vku.pulseoj.frontend.ui.master;

import com.vku.pulseoj.backend.master.MasterBackendController;
import com.vku.pulseoj.common.dto.JudgeResultDTO;
import com.vku.pulseoj.common.dto.SubmissionDTO;
import com.vku.pulseoj.common.enums.Verdict;
import net.miginfocom.swing.MigLayout;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTabbedPane;
import javax.swing.JTextArea;
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
        setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));

        // Top Stats bar
        JPanel topBar = new JPanel(new MigLayout("insets 4 8 8 8, fillx", "[grow][]", "[]"));
        lblStats.setFont(new Font("Segoe UI", Font.BOLD, 13));
        lblStats.setForeground(new Color(51, 65, 85));
        topBar.add(lblStats, "align left");

        JPanel rightControls = new JPanel(new MigLayout("insets 0, gap 10", "[][]", "[]"));
        JLabel lblHint = new JLabel("💡 Nhấp đúp chuột vào bài nộp để xem mã nguồn & chẩn đoán");
        lblHint.setFont(new Font("Segoe UI", Font.ITALIC, 11));
        lblHint.setForeground(new Color(100, 116, 139));
        rightControls.add(lblHint);

        JButton btnRefresh = new JButton("🔄 Làm Mới Hàng Đợi");
        btnRefresh.setBackground(new Color(37, 99, 235));
        btnRefresh.setForeground(Color.WHITE);
        btnRefresh.setFont(new Font("Segoe UI", Font.BOLD, 12));
        btnRefresh.addActionListener(e -> refreshSubmissionsAsync());
        rightControls.add(btnRefresh);

        topBar.add(rightControls, "align right");
        add(topBar, BorderLayout.NORTH);

        // Submissions Table
        String[] cols = {"Mã ID", "Thí Sinh", "Đề Bài", "Ngôn Ngữ", "Kết Quả (Verdict)", "Thời Gian (ms)", "Máy Chấm (Worker)", "Thời Điểm Nộp"};
        tableModel = new DefaultTableModel(cols, 0) {
            @Override
            public boolean isCellEditable(int r, int c) { return false; }
        };
        table = new JTable(tableModel);
        table.setRowHeight(30);

        // Custom Modern Badge Cell Renderer for Verdict column (index 4)
        table.getColumnModel().getColumn(4).setCellRenderer(new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
                Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
                if (value != null && !isSelected) {
                    String str = value.toString();
                    if (str.startsWith("AC") || str.contains("Accepted")) {
                        c.setBackground(new Color(222, 247, 236)); // Soft emerald
                        c.setForeground(new Color(3, 84, 63));
                    } else if (str.startsWith("WA")) {
                        c.setBackground(new Color(253, 232, 232)); // Soft red
                        c.setForeground(new Color(155, 28, 28));
                    } else if (str.startsWith("TLE")) {
                        c.setBackground(new Color(254, 240, 138)); // Soft amber
                        c.setForeground(new Color(113, 63, 18));
                    } else if (str.startsWith("RE")) {
                        c.setBackground(new Color(237, 235, 254)); // Soft purple
                        c.setForeground(new Color(85, 33, 181));
                    } else if (str.startsWith("CE")) {
                        c.setBackground(new Color(243, 244, 246)); // Soft gray
                        c.setForeground(new Color(31, 41, 55));
                    } else if (str.startsWith("RUNNING")) {
                        c.setBackground(new Color(225, 239, 254)); // Soft blue
                        c.setForeground(new Color(30, 66, 159));
                    } else {
                        c.setBackground(new Color(254, 249, 195)); // Soft yellow
                        c.setForeground(new Color(133, 77, 14));
                    }
                    c.setFont(c.getFont().deriveFont(Font.BOLD, 12f));
                }
                return c;
            }
        });

        JScrollPane scrollPane = new JScrollPane(table);
        scrollPane.setBorder(BorderFactory.createLineBorder(new Color(226, 232, 240), 1));
        add(scrollPane, BorderLayout.CENTER);

        // Double-click to inspect submission details and source code
        table.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                if (e.getClickCount() == 2) {
                    int selectedRow = table.getSelectedRow();
                    if (selectedRow >= 0) {
                        int subId = (int) tableModel.getValueAt(selectedRow, 0);
                        showSubmissionDetailsDialog(subId);
                    }
                }
            }
        });

        refreshSubmissionsAsync();
    }

    private void showSubmissionDetailsDialog(int submissionId) {
        CompletableFuture.runAsync(() -> {
            SubmissionDTO sub = controller.getSubmissionById(submissionId);
            if (sub == null) return;
            SwingUtilities.invokeLater(() -> {
                JDialog dialog = new JDialog(SwingUtilities.getWindowAncestor(this),
                        "Submission #" + sub.getId() + " - " + sub.getContestantUsername() + " [" + sub.getProblemCode() + "]",
                        JDialog.ModalityType.MODELESS);
                dialog.setSize(680, 540);
                dialog.setLocationRelativeTo(this);
                dialog.setLayout(new BorderLayout(10, 10));

                JPanel infoPanel = new JPanel(new MigLayout("insets 10, gap 8", "[][grow][][grow]", "[]"));
                infoPanel.setBorder(BorderFactory.createTitledBorder("Submission Metadata"));

                infoPanel.add(new JLabel("ID:"));
                infoPanel.add(new JLabel(String.valueOf(sub.getId())));
                infoPanel.add(new JLabel("Contestant:"));
                infoPanel.add(new JLabel(sub.getContestantUsername()));

                infoPanel.add(new JLabel("Problem:"));
                infoPanel.add(new JLabel(sub.getProblemCode()));
                infoPanel.add(new JLabel("Language:"));
                infoPanel.add(new JLabel(String.valueOf(sub.getLanguage())));

                infoPanel.add(new JLabel("Verdict:"));
                JLabel lblV = new JLabel(String.valueOf(sub.getVerdict()));
                lblV.setFont(lblV.getFont().deriveFont(Font.BOLD));
                if (sub.getVerdict() == Verdict.AC) {
                    lblV.setForeground(new Color(40, 180, 99));
                } else if (sub.getVerdict() == Verdict.PENDING || sub.getVerdict() == Verdict.RUNNING) {
                    lblV.setForeground(new Color(52, 152, 219));
                } else {
                    lblV.setForeground(new Color(231, 76, 60));
                }
                infoPanel.add(lblV);

                infoPanel.add(new JLabel("Worker Node:"));
                infoPanel.add(new JLabel(sub.getWorkerId() != null ? sub.getWorkerId() : "---"));

                infoPanel.add(new JLabel("Execution Time:"));
                infoPanel.add(new JLabel(sub.getExecutionTimeMs() + " ms"));
                infoPanel.add(new JLabel("Memory Used:"));
                infoPanel.add(new JLabel(sub.getMemoryUsedKb() + " KB"));

                JTabbedPane tabs = new JTabbedPane();

                JTextArea txtSource = new JTextArea(sub.getSourceCode() != null ? sub.getSourceCode() : "");
                txtSource.setEditable(false);
                txtSource.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
                tabs.addTab("Source Code", new JScrollPane(txtSource));

                JTextArea txtOutput = new JTextArea(sub.getCompilerOutput() != null ? sub.getCompilerOutput() : "No compiler/error output.");
                txtOutput.setEditable(false);
                txtOutput.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
                tabs.addTab("Compiler Output & Diagnostics", new JScrollPane(txtOutput));

                dialog.add(infoPanel, BorderLayout.NORTH);
                dialog.add(tabs, BorderLayout.CENTER);

                JButton btnClose = new JButton("Close");
                btnClose.addActionListener(ev -> dialog.dispose());
                JPanel south = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.RIGHT));
                south.add(btnClose);
                dialog.add(south, BorderLayout.SOUTH);

                dialog.setVisible(true);
            });
        });
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
