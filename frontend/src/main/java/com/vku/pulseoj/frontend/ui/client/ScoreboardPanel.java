package com.vku.pulseoj.frontend.ui.client;

import com.vku.pulseoj.backend.client.ClientBackendController;
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
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * [TV3] ACM-ICPC Live Scoreboard View Panel.
 * Dynamically renders rank, solved problems, penalty minutes, and per-problem status (+attempts, time).
 */
public class ScoreboardPanel extends JPanel {

    private final ClientBackendController controller;
    private final DefaultTableModel tableModel;
    private final JTable table;
    private final JLabel lblLastUpdated = new JLabel("Live Standings (ACM-ICPC Standard)");
    private final List<String> problemCodes = new ArrayList<>();

    public ScoreboardPanel(ClientBackendController controller) {
        this.controller = controller;
        setLayout(new BorderLayout(10, 10));
        setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // Top Toolbar
        JPanel topBar = new JPanel(new MigLayout("insets 5, gap 10", "[grow][]", "[]"));
        lblLastUpdated.setFont(lblLastUpdated.getFont().deriveFont(Font.BOLD, 13f));
        topBar.add(lblLastUpdated);

        JButton btnRefresh = new JButton("Refresh Scoreboard");
        btnRefresh.addActionListener(e -> refreshScoreboardAsync());
        topBar.add(btnRefresh, "align right");
        add(topBar, BorderLayout.NORTH);

        // Table
        tableModel = new DefaultTableModel() {
            @Override
            public boolean isCellEditable(int r, int c) { return false; }
        };
        table = new JTable(tableModel);
        table.setRowHeight(28);

        // Custom Cell Renderer for coloring problem cells
        table.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
                Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
                if (value != null && column >= 5) {
                    String str = value.toString();
                    if (str.startsWith("+")) {
                        c.setBackground(new Color(39, 174, 96, 80)); // Green tint
                        c.setForeground(Color.WHITE);
                        c.setFont(c.getFont().deriveFont(Font.BOLD));
                    } else if (str.startsWith("-")) {
                        c.setBackground(new Color(192, 57, 43, 80)); // Red tint
                        c.setForeground(Color.WHITE);
                    } else if (str.startsWith("?")) {
                        c.setBackground(new Color(243, 156, 18, 80)); // Yellow tint
                        c.setForeground(Color.WHITE);
                    } else {
                        c.setBackground(table.getBackground());
                        c.setForeground(table.getForeground());
                    }
                } else {
                    c.setBackground(table.getBackground());
                    c.setForeground(table.getForeground());
                }
                return c;
            }
        });

        add(new JScrollPane(table), BorderLayout.CENTER);

        refreshScoreboardAsync();
    }

    public void refreshScoreboardAsync() {
        controller.fetchScoreboardAsync().thenAccept(this::updateScoreboard);
    }

    public void updateScoreboard(List<ScoreboardRowDTO> scoreboard) {
        SwingUtilities.invokeLater(() -> {
            // Collect all unique problem codes across rows
            Set<String> uniqueCodes = new HashSet<>();
            for (ScoreboardRowDTO row : scoreboard) {
                uniqueCodes.addAll(row.getProblemDetails().keySet());
            }

            problemCodes.clear();
            problemCodes.addAll(uniqueCodes.stream().sorted().toList());

            // Build dynamic headers
            List<String> headers = new ArrayList<>();
            headers.add("Rank");
            headers.add("Username");
            headers.add("Full Name");
            headers.add("Solved");
            headers.add("Penalty (min)");
            headers.addAll(problemCodes);

            tableModel.setDataVector(new Object[0][0], headers.toArray());

            for (ScoreboardRowDTO row : scoreboard) {
                List<Object> rowData = new ArrayList<>();
                rowData.add(row.getRank());
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
        });
    }
}
