package com.vku.pulseoj.frontend.ui.client;

import com.vku.pulseoj.backend.client.ClientBackendController;
import com.vku.pulseoj.common.dto.ContestantDTO;
import com.vku.pulseoj.common.dto.ScoreboardRowDTO;
import com.vku.pulseoj.common.dto.ScoreboardRowDTO.ProblemScoreDetail;
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
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * [TV3] ACM-ICPC Live Scoreboard View Panel.
 * Synchronized with Master modern FlatLightLaf pastel badges and Vietnamese labels.
 */
public class ScoreboardPanel extends JPanel {

    private final ClientBackendController controller;
    private final DefaultTableModel tableModel;
    private final JTable table;
    private final JLabel lblLastUpdated = new JLabel("♦ Bảng Xếp Hạng Trực Tiếp Chuẩn ACM-ICPC (Penalty: +20 phút / lần nộp sai trước khi AC)");
    private final List<String> problemCodes = new ArrayList<>();
    private final SimpleDateFormat timeFmt = new SimpleDateFormat("HH:mm:ss");

    public ScoreboardPanel(ClientBackendController controller) {
        this.controller = controller;
        setLayout(new BorderLayout(10, 10));
        setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));

        // Top Toolbar
        JPanel topBar = new JPanel(new MigLayout("insets 4 8 8 8, fillx", "[grow][]", "[]"));
        lblLastUpdated.setFont(new Font("Segoe UI", Font.BOLD, 13));
        lblLastUpdated.setForeground(new Color(30, 41, 59));
        topBar.add(lblLastUpdated, "align left");

        JButton btnRefresh = new JButton("► Làm Mới Bảng Xếp Hạng");
        btnRefresh.setBackground(new Color(37, 99, 235));
        btnRefresh.setForeground(Color.WHITE);
        btnRefresh.setFont(new Font("Segoe UI", Font.BOLD, 12));
        btnRefresh.addActionListener(e -> refreshScoreboardAsync());
        topBar.add(btnRefresh, "align right");
        add(topBar, BorderLayout.NORTH);

        // Table
        tableModel = new DefaultTableModel() {
            @Override
            public boolean isCellEditable(int r, int c) { return false; }
        };
        table = new JTable(tableModel);
        table.setRowHeight(32);

        // Custom Pastel Cell Renderer for FlatLightLaf bright theme
        table.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
                Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
                if (isSelected) {
                    return c;
                }

                ContestantDTO current = controller.getCurrentContestant();
                String rowUsername = table.getColumnCount() > 1 && table.getValueAt(row, 1) != null
                        ? table.getValueAt(row, 1).toString()
                        : "";
                boolean isCurrentUserRow = current != null && rowUsername.equalsIgnoreCase(current.getUsername());

                if (value != null && column >= 5) {
                    String str = value.toString();
                    if (str.startsWith("+")) {
                        c.setBackground(new Color(220, 252, 231)); // Soft emerald
                        c.setForeground(new Color(22, 101, 52));
                        c.setFont(c.getFont().deriveFont(Font.BOLD, 12f));
                    } else if (str.startsWith("-")) {
                        c.setBackground(new Color(254, 226, 226)); // Soft red
                        c.setForeground(new Color(153, 27, 27));
                        c.setFont(c.getFont().deriveFont(Font.BOLD, 12f));
                    } else if (str.startsWith("?")) {
                        c.setBackground(new Color(254, 243, 199)); // Soft amber
                        c.setForeground(new Color(146, 64, 14));
                        c.setFont(c.getFont().deriveFont(Font.BOLD, 12f));
                    } else {
                        c.setBackground(isCurrentUserRow ? new Color(239, 246, 255) : table.getBackground());
                        c.setForeground(new Color(148, 163, 184));
                    }
                } else {
                    c.setBackground(isCurrentUserRow ? new Color(239, 246, 255) : table.getBackground());
                    c.setForeground(table.getForeground());
                    if (column == 0 || column == 3 || isCurrentUserRow) {
                        c.setFont(c.getFont().deriveFont(Font.BOLD));
                    }
                }
                return c;
            }
        });

        JScrollPane scrollPane = new JScrollPane(table);
        scrollPane.setBorder(BorderFactory.createLineBorder(new Color(226, 232, 240), 1));
        add(scrollPane, BorderLayout.CENTER);

        refreshScoreboardAsync();
    }

    public void refreshScoreboardAsync() {
        controller.fetchScoreboardAsync().thenAccept(this::updateScoreboard);
    }

    public void updateScoreboard(List<ScoreboardRowDTO> scoreboard) {
        SwingUtilities.invokeLater(() -> {
            Set<String> uniqueCodes = new HashSet<>();
            for (ScoreboardRowDTO row : scoreboard) {
                uniqueCodes.addAll(row.getProblemDetails().keySet());
            }

            problemCodes.clear();
            problemCodes.addAll(uniqueCodes.stream().sorted().toList());

            List<String> headers = new ArrayList<>();
            headers.add("Hạng");
            headers.add("Tài Khoản");
            headers.add("Họ và Tên Thí Sinh");
            headers.add("Số Bài Giải (AC)");
            headers.add("Tổng Phạt (Phút)");
            headers.addAll(problemCodes);

            tableModel.setDataVector(new Object[0][0], headers.toArray());

            for (ScoreboardRowDTO row : scoreboard) {
                List<Object> rowData = new ArrayList<>();
                rowData.add("#" + row.getRank());
                rowData.add(row.getUsername());
                rowData.add(row.getFullName());
                rowData.add(row.getSolvedCount());
                rowData.add(row.getTotalPenaltyMinutes());

                for (String code : problemCodes) {
                    ProblemScoreDetail detail = row.getProblemDetails().get(code);
                    rowData.add(detail != null ? detail.getDisplayCell() : ".");
                }

                tableModel.addRow(rowData.toArray());
            }

            lblLastUpdated.setText(String.format(
                    "♦ Bảng Xếp Hạng Trực Tiếp Chuẩn ACM-ICPC — Cập nhật lúc %s (%d thí sinh)",
                    timeFmt.format(new Date()), scoreboard.size()
            ));
        });
    }
}
