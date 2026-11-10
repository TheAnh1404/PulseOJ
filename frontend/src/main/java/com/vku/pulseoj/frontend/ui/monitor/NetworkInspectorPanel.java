package com.vku.pulseoj.frontend.ui.monitor;

import com.vku.pulseoj.common.dto.NetworkPacketLogDTO;
import net.miginfocom.swing.MigLayout;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.SwingUtilities;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.Color;
import java.util.LinkedList;

/**
 * [TV4] Real-time Network Packet Inspector.
 * Captures and displays UDP Broadcast Discovery, UDP Telemetry Heartbeats, and RMI interactions.
 */
public class NetworkInspectorPanel extends JPanel {

    private static final int MAX_PACKET_HISTORY = 300;

    private final DefaultTableModel tableModel;
    private final JTable table;
    private final JCheckBox chkAutoScroll = new JCheckBox("Auto-Scroll", true);
    private final JCheckBox chkPause = new JCheckBox("Pause Capture", false);
    private final JLabel lblPacketCount = new JLabel("Captured Packets: 0");

    private final LinkedList<NetworkPacketLogDTO> packetBuffer = new LinkedList<>();

    public NetworkInspectorPanel() {
        setLayout(new BorderLayout(5, 5));
        setBorder(BorderFactory.createTitledBorder("Nhật Ký Gói Tin Mạng & UDP Telemetry Inspector"));

        // Table
        String[] cols = {"Thời Gian", "Giao Thức", "Nguồn (Src)", "Đích (Dst)", "Bytes", "Tóm Tắt", "Nội Dung Payload"};
        tableModel = new DefaultTableModel(cols, 0) {
            @Override
            public boolean isCellEditable(int r, int c) { return false; }
        };
        table = new JTable(tableModel);
        table.setRowHeight(26);
        table.getColumnModel().getColumn(0).setPreferredWidth(85);
        table.getColumnModel().getColumn(1).setPreferredWidth(110);
        table.getColumnModel().getColumn(2).setPreferredWidth(130);
        table.getColumnModel().getColumn(3).setPreferredWidth(130);
        table.getColumnModel().getColumn(4).setPreferredWidth(60);
        table.getColumnModel().getColumn(5).setPreferredWidth(180);
        table.getColumnModel().getColumn(6).setPreferredWidth(300);

        // Toolbar
        JPanel toolbar = new JPanel(new MigLayout("insets 4 8 4 8, gap 10"));
        toolbar.setBackground(new Color(248, 250, 252));
        toolbar.add(lblPacketCount);
        toolbar.add(chkAutoScroll);
        toolbar.add(chkPause);

        JButton btnClear = new JButton("🗑️ Xóa Nhật Ký");
        btnClear.setBackground(new Color(241, 245, 249));
        btnClear.setForeground(new Color(51, 65, 85));
        btnClear.addActionListener(e -> {
            tableModel.setRowCount(0);
            packetBuffer.clear();
            lblPacketCount.setText("Captured Packets: 0");
        });
        toolbar.add(btnClear);
        add(toolbar, BorderLayout.NORTH);

        JScrollPane scrollPane = new JScrollPane(table);
        scrollPane.setBorder(BorderFactory.createLineBorder(new Color(226, 232, 240), 1));
        add(scrollPane, BorderLayout.CENTER);
    }

    public void logPacket(NetworkPacketLogDTO pkt) {
        if (chkPause.isSelected()) return;

        SwingUtilities.invokeLater(() -> {
            if (tableModel.getRowCount() >= MAX_PACKET_HISTORY) {
                tableModel.removeRow(0);
            }

            tableModel.addRow(new Object[]{
                    pkt.getFormattedTime(),
                    pkt.getProtocol(),
                    pkt.getSource(),
                    pkt.getDestination(),
                    pkt.getPacketSizeBytes(),
                    pkt.getSummary(),
                    pkt.getPayloadSnippet()
            });

            lblPacketCount.setText("Captured Packets: " + tableModel.getRowCount());

            if (chkAutoScroll.isSelected() && table.getRowCount() > 0) {
                table.scrollRectToVisible(table.getCellRect(table.getRowCount() - 1, 0, true));
            }
        });
    }
}
