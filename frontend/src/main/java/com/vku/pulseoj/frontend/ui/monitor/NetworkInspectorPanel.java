package com.vku.pulseoj.frontend.ui.monitor;

import com.vku.pulseoj.common.dto.NetworkPacketLogDTO;
import net.miginfocom.swing.MigLayout;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.Font;
import java.util.LinkedList;

/**
 * [TV4] Real-time Network Packet Inspector.
 * Captures and displays UDP Broadcast Discovery, UDP Telemetry Heartbeats, and RMI interactions.
 * Includes Protocol Filter, Pastel Protocol Badges, and Double-click Payload Inspection Modal.
 */
public class NetworkInspectorPanel extends JPanel {

    private static final int MAX_PACKET_HISTORY = 300;

    private final DefaultTableModel tableModel;
    private final JTable table;
    private final JComboBox<String> cbProtocolFilter = new JComboBox<>(new String[]{
            "Tất cả giao thức", "UDP-DISCOVERY", "UDP-HEARTBEAT", "RMI"
    });
    private final JCheckBox chkAutoScroll = new JCheckBox("Tự cuộn dòng mới", true);
    private final JCheckBox chkPause = new JCheckBox("Tạm dừng bắt gói", false);
    private final JLabel lblPacketCount = new JLabel("Gói tin hiển thị: 0");

    private final LinkedList<NetworkPacketLogDTO> packetBuffer = new LinkedList<>();

    public NetworkInspectorPanel() {
        setLayout(new BorderLayout(5, 5));
        setBorder(BorderFactory.createTitledBorder("Nhật Ký Gói Tin Mạng & UDP Telemetry Inspector (Nhấp đúp dòng để xem chi tiết Payload)"));

        // Table
        String[] cols = {"Thời Gian", "Giao Thức", "Nguồn (Src)", "Đích (Dst)", "Kích Thước (B)", "Tóm Tắt Bản Tin", "Nội Dung Payload"};
        tableModel = new DefaultTableModel(cols, 0) {
            @Override
            public boolean isCellEditable(int r, int c) { return false; }
        };
        table = new JTable(tableModel);
        table.setRowHeight(28);
        table.getColumnModel().getColumn(0).setPreferredWidth(85);
        table.getColumnModel().getColumn(1).setPreferredWidth(125);
        table.getColumnModel().getColumn(2).setPreferredWidth(130);
        table.getColumnModel().getColumn(3).setPreferredWidth(130);
        table.getColumnModel().getColumn(4).setPreferredWidth(105);
        table.getColumnModel().getColumn(5).setPreferredWidth(190);
        table.getColumnModel().getColumn(6).setPreferredWidth(280);

        // Pastel Badge Cell Renderer for Protocol column (index 1)
        table.getColumnModel().getColumn(1).setCellRenderer(new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
                Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
                if (value != null && !isSelected) {
                    String proto = value.toString();
                    if (proto.contains("DISCOVERY")) {
                        c.setBackground(new Color(237, 235, 254)); // Soft purple
                        c.setForeground(new Color(85, 33, 181));
                    } else if (proto.contains("HEARTBEAT")) {
                        c.setBackground(new Color(222, 247, 236)); // Soft emerald
                        c.setForeground(new Color(3, 84, 63));
                    } else {
                        c.setBackground(new Color(225, 239, 254)); // Soft blue
                        c.setForeground(new Color(30, 66, 159));
                    }
                    c.setFont(c.getFont().deriveFont(Font.BOLD, 11f));
                }
                return c;
            }
        });

        // Double-click row to inspect packet details
        table.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                if (e.getClickCount() == 2) {
                    int r = table.getSelectedRow();
                    if (r >= 0) {
                        showPacketDetailsDialog(r);
                    }
                }
            }
        });

        // Toolbar
        JPanel toolbar = new JPanel(new MigLayout("insets 4 8 4 8, gap 12, fillx", "[][][][][][grow][]", "[]"));
        toolbar.setBackground(new Color(248, 250, 252));

        lblPacketCount.setFont(new Font("Segoe UI", Font.BOLD, 12));
        lblPacketCount.setForeground(new Color(30, 41, 59));
        toolbar.add(lblPacketCount);

        toolbar.add(new JLabel("Lọc giao thức:"));
        cbProtocolFilter.addActionListener(e -> rebuildFilteredTable());
        toolbar.add(cbProtocolFilter);

        toolbar.add(chkAutoScroll);
        toolbar.add(chkPause);

        JButton btnClear = new JButton("× Xóa Nhật Ký");
        btnClear.setBackground(new Color(241, 245, 249));
        btnClear.setForeground(new Color(51, 65, 85));
        btnClear.addActionListener(e -> {
            tableModel.setRowCount(0);
            packetBuffer.clear();
            lblPacketCount.setText("Gói tin hiển thị: 0");
        });
        toolbar.add(btnClear, "skip 1, align right");
        add(toolbar, BorderLayout.NORTH);

        JScrollPane scrollPane = new JScrollPane(table);
        scrollPane.setBorder(BorderFactory.createLineBorder(new Color(226, 232, 240), 1));
        add(scrollPane, BorderLayout.CENTER);
    }

    private boolean matchesFilter(NetworkPacketLogDTO pkt) {
        int idx = cbProtocolFilter.getSelectedIndex();
        if (idx <= 0) return true;
        String filter = (String) cbProtocolFilter.getSelectedItem();
        return filter != null && pkt.getProtocol() != null && pkt.getProtocol().toUpperCase().contains(filter.toUpperCase());
    }

    private void rebuildFilteredTable() {
        tableModel.setRowCount(0);
        for (NetworkPacketLogDTO pkt : packetBuffer) {
            if (matchesFilter(pkt)) {
                appendRow(pkt);
            }
        }
        lblPacketCount.setText("Gói tin hiển thị: " + tableModel.getRowCount() + " / " + packetBuffer.size());
    }

    private void appendRow(NetworkPacketLogDTO pkt) {
        tableModel.addRow(new Object[]{
                pkt.getFormattedTime(),
                pkt.getProtocol(),
                pkt.getSource(),
                pkt.getDestination(),
                pkt.getPacketSizeBytes(),
                pkt.getSummary(),
                pkt.getPayloadSnippet()
        });
    }

    private void showPacketDetailsDialog(int row) {
        String time = String.valueOf(tableModel.getValueAt(row, 0));
        String proto = String.valueOf(tableModel.getValueAt(row, 1));
        String src = String.valueOf(tableModel.getValueAt(row, 2));
        String dst = String.valueOf(tableModel.getValueAt(row, 3));
        String bytes = String.valueOf(tableModel.getValueAt(row, 4));
        String summary = String.valueOf(tableModel.getValueAt(row, 5));
        String payload = String.valueOf(tableModel.getValueAt(row, 6));

        JDialog dialog = new JDialog(SwingUtilities.getWindowAncestor(this),
                "Chi Tiết Gói Tin Mạng [" + proto + "] lúc " + time,
                JDialog.ModalityType.MODELESS);
        dialog.setSize(560, 360);
        dialog.setLocationRelativeTo(this);
        dialog.setLayout(new BorderLayout(10, 10));

        JPanel metaPanel = new JPanel(new MigLayout("insets 10, gap 8", "[][grow][][grow]", "[]"));
        metaPanel.setBorder(BorderFactory.createTitledBorder("Siêu Dữ Liệu Gói Tin (Packet Header)"));
        metaPanel.add(new JLabel("Thời điểm:"));
        metaPanel.add(new JLabel(time));
        metaPanel.add(new JLabel("Giao thức:"));
        metaPanel.add(new JLabel(proto), "wrap");
        metaPanel.add(new JLabel("Địa chỉ Nguồn:"));
        metaPanel.add(new JLabel(src));
        metaPanel.add(new JLabel("Địa chỉ Đích:"));
        metaPanel.add(new JLabel(dst), "wrap");
        metaPanel.add(new JLabel("Kích thước:"));
        metaPanel.add(new JLabel(bytes + " bytes"));
        metaPanel.add(new JLabel("Tóm tắt:"));
        metaPanel.add(new JLabel(summary), "wrap");

        JTextArea txtPayload = new JTextArea(payload);
        txtPayload.setEditable(false);
        txtPayload.setLineWrap(true);
        txtPayload.setWrapStyleWord(true);
        txtPayload.setFont(new Font("Consolas", Font.PLAIN, 13));
        JScrollPane scroll = new JScrollPane(txtPayload);
        scroll.setBorder(BorderFactory.createTitledBorder("Nội Dung Payload Chi Tiết"));

        dialog.add(metaPanel, BorderLayout.NORTH);
        dialog.add(scroll, BorderLayout.CENTER);

        JButton btnClose = new JButton("Đóng");
        btnClose.addActionListener(e -> dialog.dispose());
        JPanel south = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        south.add(btnClose);
        dialog.add(south, BorderLayout.SOUTH);

        dialog.setVisible(true);
    }

    public void logPacket(NetworkPacketLogDTO pkt) {
        if (chkPause.isSelected()) return;

        SwingUtilities.invokeLater(() -> {
            if (packetBuffer.size() >= MAX_PACKET_HISTORY) {
                packetBuffer.removeFirst();
            }
            packetBuffer.addLast(pkt);

            if (matchesFilter(pkt)) {
                if (tableModel.getRowCount() >= MAX_PACKET_HISTORY) {
                    tableModel.removeRow(0);
                }
                appendRow(pkt);

                if (chkAutoScroll.isSelected() && table.getRowCount() > 0) {
                    table.scrollRectToVisible(table.getCellRect(table.getRowCount() - 1, 0, true));
                }
            }

            lblPacketCount.setText("Gói tin hiển thị: " + tableModel.getRowCount() + " / " + packetBuffer.size());
        });
    }
}
