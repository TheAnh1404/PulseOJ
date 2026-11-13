package com.vku.pulseoj.frontend.ui.client;

import com.vku.pulseoj.backend.client.ClientBackendController;
import com.vku.pulseoj.backend.client.ClientUiListener;
import com.vku.pulseoj.common.dto.ContestantDTO;
import com.vku.pulseoj.common.dto.JudgeResultDTO;
import com.vku.pulseoj.common.dto.ProblemDTO;
import com.vku.pulseoj.common.dto.ScoreboardRowDTO;
import com.vku.pulseoj.common.dto.SubmissionDTO;
import com.vku.pulseoj.common.dto.TestCaseDTO;
import com.vku.pulseoj.common.enums.Language;
import com.vku.pulseoj.common.enums.Verdict;
import net.miginfocom.swing.MigLayout;
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.fife.ui.rsyntaxtextarea.SyntaxConstants;
import org.fife.ui.rtextarea.RTextScrollPane;

import javax.swing.BorderFactory;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * [TV3] Contestant Workspace Main Window.
 * Synchronized with Master modern FlatLightLaf design, Top Header Banner,
 * Pastel ICPC Verdict Badges, and Double-click Submission History Inspection Modal.
 */
public class ClientMainFrame extends JFrame implements ClientUiListener {

    private final ClientBackendController controller;
    private final List<ProblemDTO> problemsList = new ArrayList<>();
    private final Map<Integer, SubmissionDTO> submissionHistoryCache = new ConcurrentHashMap<>();
    private ProblemDTO currentProblem;

    // Header Chips
    private final JLabel lblContestantChip = new JLabel();
    private final JLabel lblConnectionChip = new JLabel("● RMI Callback: ĐÃ KẾT NỐI");

    // Column 1: Problem Browser
    private final JComboBox<String> cbProblems = new JComboBox<>();
    private final JLabel lblProblemTitle = new JLabel("Chọn một đề bài");
    private final JLabel lblProblemLimits = new JLabel("Giới hạn Thời gian: --- | Bộ nhớ: ---");
    private final JTextArea txtDescription = new JTextArea();
    private final DefaultTableModel sampleTestsModel;
    private final JTable sampleTestsTable;

    // Column 2: Code Editor
    private final JComboBox<Language> cbLanguage = new JComboBox<>(Language.values());
    private final RSyntaxTextArea codeEditor = new RSyntaxTextArea();
    private final JButton btnSubmit = new JButton("► Nộp Bài Chấm (Submit Solution)");
    private final JButton btnResetCode = new JButton("► Khôi Phục Code Mẫu");

    // Column 3: Live Verdicts & Submission History
    private final JLabel lblLiveVerdict = new JLabel("Sẵn sàng nộp bài.");
    private final JProgressBar pbJudging = new JProgressBar(0, 100);
    private final DefaultTableModel testProgressModel;
    private final JTable testProgressTable;
    private final DefaultTableModel historyModel;
    private final JTable historyTable;

    // Scoreboard Panel
    private final ScoreboardPanel scoreboardPanel;

    public ClientMainFrame(ClientBackendController controller) {
        super("PulseOJ — Không Gian Làm Bài Thí Sinh (" +
                (controller.getCurrentContestant() != null ? controller.getCurrentContestant().getFullName() : "Contestant") + ")");
        this.controller = controller;
        this.controller.setUiListener(this);

        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1280, 820);
        setMinimumSize(new Dimension(1020, 660));
        setLocationRelativeTo(null);

        // 0. Top Header Banner (Synchronized with MasterMainFrame)
        JPanel headerBanner = new JPanel(new MigLayout("insets 12 18 12 18, fillx", "[grow][]", "[]"));
        headerBanner.setBackground(Color.WHITE);
        headerBanner.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(226, 232, 240)));

        JPanel titleBlock = new JPanel(new MigLayout("insets 0, gap 2", "[grow]", "[][]"));
        titleBlock.setBackground(Color.WHITE);
        JLabel lblAppTitle = new JLabel("♦ PULSE OJ — CONTESTANT WORKSPACE");
        lblAppTitle.setFont(new Font("Segoe UI", Font.BOLD, 18));
        lblAppTitle.setForeground(new Color(15, 23, 42));
        lblAppTitle.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 14));

        JLabel lblAppSubtitle = new JLabel("Không Gian Thi Lập Trình Thời Gian Thực • Phản Hồi RMI Callback Từng Test Case • Bảng Xếp Hạng ACM-ICPC");
        lblAppSubtitle.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        lblAppSubtitle.setForeground(new Color(100, 116, 139));
        lblAppSubtitle.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 14));

        titleBlock.add(lblAppTitle, "wrap");
        titleBlock.add(lblAppSubtitle);
        headerBanner.add(titleBlock, "align left");

        JPanel chipsPanel = new JPanel(new MigLayout("insets 0, gap 10", "[][]", "[]"));
        chipsPanel.setBackground(Color.WHITE);

        ContestantDTO c = controller.getCurrentContestant();
        String userDisplay = (c != null) ? ("■ " + c.getFullName() + " (@" + c.getUsername() + ")") : "■ Thí sinh";
        lblContestantChip.setText(userDisplay);
        lblContestantChip.setOpaque(true);
        lblContestantChip.setBackground(new Color(239, 246, 255));
        lblContestantChip.setForeground(new Color(29, 78, 216));
        lblContestantChip.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(191, 219, 254), 1),
                BorderFactory.createEmptyBorder(6, 12, 6, 12)
        ));
        lblContestantChip.setFont(new Font("Segoe UI", Font.BOLD, 12));

        styleConnectionChip(true);

        chipsPanel.add(lblContestantChip);
        chipsPanel.add(lblConnectionChip);
        headerBanner.add(chipsPanel, "align right");

        // 1. Column 1: Problem Panel
        JPanel col1 = new JPanel(new BorderLayout(6, 6));
        col1.setBorder(BorderFactory.createTitledBorder("1. Đề Bài & Test Mẫu (Problem Statement)"));
        col1.setPreferredSize(new Dimension(370, 700));

        JPanel col1Header = new JPanel(new MigLayout("insets 6, gap 5, fillx", "[grow][]", "[]"));
        col1Header.add(cbProblems, "growx");
        JButton btnReloadProblems = new JButton("Tải lại");
        btnReloadProblems.setToolTipText("Tải lại danh sách đề bài từ Master");
        btnReloadProblems.addActionListener(e -> loadProblemsAsync());
        col1Header.add(btnReloadProblems, "wrap");

        lblProblemTitle.setFont(new Font("Segoe UI", Font.BOLD, 14));
        lblProblemTitle.setForeground(new Color(15, 23, 42));
        col1Header.add(lblProblemTitle, "span 2, wrap");

        lblProblemLimits.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        lblProblemLimits.setForeground(new Color(100, 116, 139));
        col1Header.add(lblProblemLimits, "span 2, wrap");
        col1.add(col1Header, BorderLayout.NORTH);

        txtDescription.setEditable(false);
        txtDescription.setLineWrap(true);
        txtDescription.setWrapStyleWord(true);
        txtDescription.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        txtDescription.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        JScrollPane descScroll = new JScrollPane(txtDescription);

        String[] sampleCols = {"#", "Đầu Vào (Input)", "Đầu Ra Mẫu (Expected)"};
        sampleTestsModel = new DefaultTableModel(sampleCols, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };
        sampleTestsTable = new JTable(sampleTestsModel);
        sampleTestsTable.setRowHeight(26);
        sampleTestsTable.getColumnModel().getColumn(0).setMaxWidth(45);
        JScrollPane sampleScroll = new JScrollPane(sampleTestsTable);
        sampleScroll.setBorder(BorderFactory.createTitledBorder("Bộ Test Mẫu (Sample Test Cases)"));
        sampleScroll.setPreferredSize(new Dimension(360, 175));

        JSplitPane col1Split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, descScroll, sampleScroll);
        col1Split.setResizeWeight(0.68);
        col1.add(col1Split, BorderLayout.CENTER);

        // 2. Column 2: Code Editor (RSyntaxTextArea)
        JPanel col2 = new JPanel(new BorderLayout(6, 6));
        col2.setBorder(BorderFactory.createTitledBorder("2. Trình Soạn Thảo Mã Nguồn (Source Code Editor)"));
        col2.setPreferredSize(new Dimension(490, 700));

        JPanel editorToolbar = new JPanel(new MigLayout("insets 5, gap 8, fillx", "[][grow][]", "[]"));
        editorToolbar.add(new JLabel("Ngôn ngữ:"));
        editorToolbar.add(cbLanguage);
        btnResetCode.setBackground(new Color(241, 245, 249));
        btnResetCode.setForeground(new Color(51, 65, 85));
        editorToolbar.add(btnResetCode, "align right");
        col2.add(editorToolbar, BorderLayout.NORTH);

        codeEditor.setSyntaxEditingStyle(SyntaxConstants.SYNTAX_STYLE_JAVA);
        codeEditor.setCodeFoldingEnabled(true);
        codeEditor.setAntiAliasingEnabled(true);
        codeEditor.setFont(new Font("Consolas", Font.PLAIN, 14));
        col2.add(new RTextScrollPane(codeEditor), BorderLayout.CENTER);

        JPanel submitBar = new JPanel(new MigLayout("insets 8, gap 10, fillx", "[grow]", "[]"));
        btnSubmit.setFont(new Font("Segoe UI", Font.BOLD, 14));
        btnSubmit.setBackground(new Color(22, 163, 74)); // Emerald-600
        btnSubmit.setForeground(Color.WHITE);
        btnSubmit.setPreferredSize(new Dimension(180, 38));
        submitBar.add(btnSubmit, "growx");
        col2.add(submitBar, BorderLayout.SOUTH);

        // 3. Column 3: Live Judging & History
        JPanel col3 = new JPanel(new BorderLayout(6, 6));
        col3.setBorder(BorderFactory.createTitledBorder("3. Tiến Trình Chấm Trực Tiếp & Lịch Sử Nộp Bài"));
        col3.setPreferredSize(new Dimension(390, 700));

        JPanel liveStatusPanel = new JPanel(new MigLayout("insets 8, gap 6, fillx", "[grow]", "[]"));
        lblLiveVerdict.setFont(new Font("Segoe UI", Font.BOLD, 13));
        lblLiveVerdict.setForeground(new Color(30, 41, 59));
        liveStatusPanel.add(lblLiveVerdict, "wrap");
        pbJudging.setStringPainted(true);
        pbJudging.setString("Chưa có bài chấm (Idle)");
        pbJudging.setPreferredSize(new Dimension(300, 24));
        liveStatusPanel.add(pbJudging, "growx");
        col3.add(liveStatusPanel, BorderLayout.NORTH);

        String[] progCols = {"Test #", "Phán Quyết", "Thời Gian (ms)"};
        testProgressModel = new DefaultTableModel(progCols, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };
        testProgressTable = new JTable(testProgressModel);
        testProgressTable.setRowHeight(26);
        setupPastelVerdictRenderer(testProgressTable, 1);
        JScrollPane progScroll = new JScrollPane(testProgressTable);
        progScroll.setBorder(BorderFactory.createTitledBorder("Kết Quả Từng Test (RMI Callback Tức Thời)"));

        String[] histCols = {"Mã ID", "Đề Bài", "Kết Quả", "Thời Gian (ms)"};
        historyModel = new DefaultTableModel(histCols, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };
        historyTable = new JTable(historyModel);
        historyTable.setRowHeight(28);
        setupPastelVerdictRenderer(historyTable, 2);

        // Double-click history row to view submitted code & compiler diagnostics
        historyTable.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                if (e.getClickCount() == 2) {
                    int row = historyTable.getSelectedRow();
                    if (row >= 0) {
                        int subId = (int) historyModel.getValueAt(row, 0);
                        showSubmissionDetailsDialog(subId);
                    }
                }
            }
        });

        JScrollPane histScroll = new JScrollPane(historyTable);
        histScroll.setBorder(BorderFactory.createTitledBorder("Lịch Sử Nộp Bài Của Tôi (Nhấp đúp xem Code/Lỗi)"));

        JSplitPane col3Split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, progScroll, histScroll);
        col3Split.setResizeWeight(0.48);
        col3.add(col3Split, BorderLayout.CENTER);

        // Assemble 3 Columns with SplitPanes
        JSplitPane rightTwoCols = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, col2, col3);
        rightTwoCols.setResizeWeight(0.55);

        JSplitPane mainThreeCols = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, col1, rightTwoCols);
        mainThreeCols.setResizeWeight(0.30);

        // Main Tabbed Interface (Coding Workspace + Scoreboard)
        scoreboardPanel = new ScoreboardPanel(controller);

        JTabbedPane mainTabs = new JTabbedPane();
        mainTabs.setFont(new Font("Segoe UI", Font.BOLD, 13));
        mainTabs.addTab("Không Gian Làm Bài (3 Cột)", mainThreeCols);
        mainTabs.addTab("Bảng Xếp Hạng Trực Tiếp (ACM-ICPC)", scoreboardPanel);

        setLayout(new BorderLayout());
        add(headerBanner, BorderLayout.NORTH);
        add(mainTabs, BorderLayout.CENTER);

        // Event Listeners
        cbProblems.addActionListener(e -> onProblemSelectionChanged());
        cbLanguage.addActionListener(e -> onLanguageChanged());
        btnResetCode.addActionListener(e -> resetTemplateCode());
        btnSubmit.addActionListener(e -> submitCurrentCode());

        // Initial Data Fetch
        loadProblemsAsync();
        refreshHistoryAsync();
    }

    private void styleConnectionChip(boolean connected) {
        lblConnectionChip.setOpaque(true);
        lblConnectionChip.setFont(new Font("Segoe UI", Font.BOLD, 12));
        if (connected) {
            lblConnectionChip.setText("● RMI Callback: ĐÃ KẾT NỐI");
            lblConnectionChip.setBackground(new Color(236, 253, 245));
            lblConnectionChip.setForeground(new Color(5, 150, 105));
            lblConnectionChip.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(new Color(167, 243, 208), 1),
                    BorderFactory.createEmptyBorder(6, 12, 6, 12)
            ));
        } else {
            lblConnectionChip.setText("× RMI Callback: MẤT KẾT NỐI");
            lblConnectionChip.setBackground(new Color(254, 242, 242));
            lblConnectionChip.setForeground(new Color(220, 38, 38));
            lblConnectionChip.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(new Color(254, 202, 202), 1),
                    BorderFactory.createEmptyBorder(6, 12, 6, 12)
            ));
        }
    }

    private void loadProblemsAsync() {
        controller.fetchProblemsAsync().thenAccept(list -> {
            SwingUtilities.invokeLater(() -> {
                problemsList.clear();
                problemsList.addAll(list);

                DefaultComboBoxModel<String> model = new DefaultComboBoxModel<>();
                for (ProblemDTO p : problemsList) {
                    model.addElement(p.getCode() + " — " + p.getTitle());
                }
                cbProblems.setModel(model);

                if (!problemsList.isEmpty()) {
                    cbProblems.setSelectedIndex(0);
                    onProblemSelectionChanged();
                }
            });
        });
    }

    private void onProblemSelectionChanged() {
        int idx = cbProblems.getSelectedIndex();
        if (idx < 0 || idx >= problemsList.size()) return;

        currentProblem = problemsList.get(idx);
        lblProblemTitle.setText("♦ [" + currentProblem.getCode() + "] " + currentProblem.getTitle());
        lblProblemLimits.setText(String.format("Giới hạn thời gian: %d ms  |  Giới hạn bộ nhớ: %d MB",
                currentProblem.getTimeLimitMs(), currentProblem.getMemoryLimitMb()));
        txtDescription.setText(currentProblem.getDescription());

        sampleTestsModel.setRowCount(0);
        for (TestCaseDTO tc : currentProblem.getTestCases()) {
            if (tc.isSample()) {
                sampleTestsModel.addRow(new Object[]{
                        tc.getTestOrder(),
                        tc.getInputData().replace("\n", " "),
                        tc.getExpectedOutput().replace("\n", " ")
                });
            }
        }

        resetTemplateCode();
    }

    private void onLanguageChanged() {
        Language lang = (Language) cbLanguage.getSelectedItem();
        if (lang == null) return;

        switch (lang) {
            case JAVA -> codeEditor.setSyntaxEditingStyle(SyntaxConstants.SYNTAX_STYLE_JAVA);
            case CPP -> codeEditor.setSyntaxEditingStyle(SyntaxConstants.SYNTAX_STYLE_CPLUSPLUS);
            case PYTHON -> codeEditor.setSyntaxEditingStyle(SyntaxConstants.SYNTAX_STYLE_PYTHON);
        }
        resetTemplateCode();
    }

    private void resetTemplateCode() {
        Language lang = (Language) cbLanguage.getSelectedItem();
        if (lang == null) return;

        String template = switch (lang) {
            case JAVA -> """
                    import java.util.Scanner;

                    public class Main {
                        public static void main(String[] args) {
                            Scanner sc = new Scanner(System.in);
                            if (sc.hasNextLong()) {
                                long a = sc.nextLong();
                                long b = sc.nextLong();
                                System.out.println(a + b);
                            }
                        }
                    }
                    """;
            case CPP -> """
                    #include <iostream>
                    using namespace std;

                    int main() {
                        ios_base::sync_with_stdio(false);
                        cin.tie(NULL);
                        long long a, b;
                        if (cin >> a >> b) {
                            cout << (a + b) << "\\n";
                        }
                        return 0;
                    }
                    """;
            case PYTHON -> """
                    import sys

                    def main():
                        lines = sys.stdin.read().split()
                        if len(lines) >= 2:
                            a = int(lines[0])
                            b = int(lines[1])
                            print(a + b)

                    if __name__ == '__main__':
                        main()
                    """;
        };
        codeEditor.setText(template);
    }

    private void submitCurrentCode() {
        if (currentProblem == null) {
            JOptionPane.showMessageDialog(this, "Vui lòng chọn một đề bài trước khi nộp.", "Cảnh Báo", JOptionPane.WARNING_MESSAGE);
            return;
        }

        String sourceCode = codeEditor.getText().trim();
        if (sourceCode.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Không thể nộp mã nguồn trống.", "Cảnh Báo", JOptionPane.WARNING_MESSAGE);
            return;
        }

        Language lang = (Language) cbLanguage.getSelectedItem();
        btnSubmit.setEnabled(false);
        testProgressModel.setRowCount(0);
        lblLiveVerdict.setText("► Đang gửi bài nộp lên hàng đợi Master...");
        lblLiveVerdict.setForeground(new Color(217, 119, 6));
        pbJudging.setValue(0);
        pbJudging.setString("Đang xếp hàng đợi (Queued)...");

        controller.submitSolutionAsync(currentProblem, lang, sourceCode)
                .thenAccept(sub -> {
                    submissionHistoryCache.put(sub.getId(), sub);
                    SwingUtilities.invokeLater(() -> {
                        lblLiveVerdict.setText(String.format("► Bài nộp #%d đã vào hàng đợi. Đang chờ máy chấm Worker...", sub.getId()));
                        refreshHistoryAsync();
                    });
                })
                .exceptionally(ex -> {
                    SwingUtilities.invokeLater(() -> {
                        btnSubmit.setEnabled(true);
                        lblLiveVerdict.setText("× Lỗi nộp bài: " + ex.getMessage());
                        lblLiveVerdict.setForeground(new Color(220, 38, 38));
                    });
                    return null;
                });
    }

    private void refreshHistoryAsync() {
        controller.fetchSubmissionHistoryAsync().thenAccept(list -> {
            SwingUtilities.invokeLater(() -> {
                historyModel.setRowCount(0);
                for (SubmissionDTO s : list) {
                    submissionHistoryCache.put(s.getId(), s);
                    historyModel.addRow(new Object[]{
                            s.getId(), s.getProblemCode(), s.getVerdict().name(), s.getExecutionTimeMs()
                    });
                }
            });
        });
    }

    private void showSubmissionDetailsDialog(int submissionId) {
        SubmissionDTO sub = submissionHistoryCache.get(submissionId);
        if (sub == null) return;

        JDialog dialog = new JDialog(this,
                "Chi Tiết Bài Nộp #" + sub.getId() + " — Đề: " + sub.getProblemCode() + " (" + sub.getLanguage() + ")",
                JDialog.ModalityType.MODELESS);
        dialog.setSize(700, 530);
        dialog.setLocationRelativeTo(this);
        dialog.setLayout(new BorderLayout(10, 10));

        JPanel infoPanel = new JPanel(new MigLayout("insets 10 14 10 14, gap 10", "[right][grow,fill][right][grow,fill][right][grow,fill]", "[][]"));
        infoPanel.setBorder(BorderFactory.createTitledBorder("Thông Tin Bài Nộp"));

        infoPanel.add(new JLabel("Mã Bài Nộp:"));
        infoPanel.add(new JLabel("#" + sub.getId()));
        infoPanel.add(new JLabel("Đề Bài:"));
        infoPanel.add(new JLabel(sub.getProblemCode()));
        infoPanel.add(new JLabel("Ngôn Ngữ:"));
        infoPanel.add(new JLabel(String.valueOf(sub.getLanguage())), "wrap");

        infoPanel.add(new JLabel("Kết Quả:"));
        JLabel lblV = new JLabel(String.valueOf(sub.getVerdict()));
        lblV.setFont(new Font("Segoe UI", Font.BOLD, 12));
        lblV.setForeground(sub.getVerdict() == Verdict.AC ? new Color(5, 150, 105) : new Color(220, 38, 38));
        infoPanel.add(lblV);

        infoPanel.add(new JLabel("Thời Gian:"));
        infoPanel.add(new JLabel(sub.getExecutionTimeMs() + " ms"));
        infoPanel.add(new JLabel("Trạm Chấm:"));
        infoPanel.add(new JLabel(sub.getWorkerId() != null ? sub.getWorkerId() : "---"), "wrap");

        JTabbedPane tabs = new JTabbedPane();

        JTextArea txtSource = new JTextArea(sub.getSourceCode() != null ? sub.getSourceCode() : "");
        txtSource.setEditable(false);
        txtSource.setFont(new Font("Consolas", Font.PLAIN, 13));
        tabs.addTab("Mã Nguồn Đã Nộp", new JScrollPane(txtSource));

        JTextArea txtOutput = new JTextArea(
                (sub.getCompilerOutput() != null && !sub.getCompilerOutput().isBlank())
                        ? sub.getCompilerOutput()
                        : "Không có lỗi biên dịch hoặc thông báo lỗi hệ thống."
        );
        txtOutput.setEditable(false);
        txtOutput.setFont(new Font("Consolas", Font.PLAIN, 13));
        tabs.addTab("Thông Báo Biên Dịch & Chẩn Đoán", new JScrollPane(txtOutput));

        dialog.add(infoPanel, BorderLayout.NORTH);
        dialog.add(tabs, BorderLayout.CENTER);

        JButton btnLoadToEditor = new JButton("► Nạp Lại Vào Trình Soạn Thảo");
        btnLoadToEditor.setBackground(new Color(37, 99, 235));
        btnLoadToEditor.setForeground(Color.WHITE);
        btnLoadToEditor.addActionListener(ev -> {
            if (sub.getSourceCode() != null) {
                codeEditor.setText(sub.getSourceCode());
            }
            dialog.dispose();
        });

        JButton btnClose = new JButton("Đóng");
        btnClose.addActionListener(ev -> dialog.dispose());

        JPanel south = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        south.add(btnLoadToEditor);
        south.add(btnClose);
        dialog.add(south, BorderLayout.SOUTH);

        dialog.setVisible(true);
    }

    /**
     * Synchronized Pastel Verdict Cell Renderer matching Master's SubmissionQueuePanel.
     */
    private void setupPastelVerdictRenderer(JTable table, int colIndex) {
        table.getColumnModel().getColumn(colIndex).setCellRenderer(new DefaultTableCellRenderer() {
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
                    } else {
                        c.setBackground(new Color(254, 249, 195)); // Soft yellow
                        c.setForeground(new Color(133, 77, 14));
                    }
                    c.setFont(c.getFont().deriveFont(Font.BOLD, 12f));
                } else if (!isSelected) {
                    c.setBackground(table.getBackground());
                    c.setForeground(table.getForeground());
                }
                return c;
            }
        });
    }

    // =========================================================================
    // ClientUiListener Callbacks (Dispatched safely on Swing EDT)
    // =========================================================================

    @Override
    public void onTestProgress(JudgeResultDTO result) {
        SwingUtilities.invokeLater(() -> {
            if (result.getCurrentTestIndex() <= 1) {
                testProgressModel.setRowCount(0);
            }
            int total = Math.max(1, result.getTotalTests());
            int percent = (int) (((double) result.getCurrentTestIndex() / total) * 100);
            pbJudging.setValue(percent);
            pbJudging.setString(String.format("Đang chấm Test %d/%d (%d%%)", result.getCurrentTestIndex(), result.getTotalTests(), percent));

            testProgressModel.addRow(new Object[]{
                    "Test #" + result.getCurrentTestIndex(),
                    result.getVerdict().name(),
                    result.getExecutionTimeMs()
            });

            String workerTag = (result.getWorkerId() != null && !result.getWorkerId().isBlank())
                    ? " [" + result.getWorkerId() + "]"
                    : "";
            lblLiveVerdict.setText(String.format("► Bài #%d — Test %d/%d → %s%s",
                    result.getSubmissionId(), result.getCurrentTestIndex(), result.getTotalTests(), result.getVerdict(), workerTag));
            lblLiveVerdict.setForeground(new Color(37, 99, 235));
        });
    }

    @Override
    public void onFinalVerdict(SubmissionDTO submission) {
        submissionHistoryCache.put(submission.getId(), submission);
        SwingUtilities.invokeLater(() -> {
            btnSubmit.setEnabled(true);
            pbJudging.setValue(100);
            pbJudging.setString("Kết quả cuối: " + submission.getVerdict().name());

            Verdict v = submission.getVerdict();
            lblLiveVerdict.setText(String.format("[OK] Bài #%d: %s (%s) — %d ms",
                    submission.getId(), v.name(), v.getDescription(), submission.getExecutionTimeMs()));

            if (v == Verdict.AC) {
                lblLiveVerdict.setForeground(new Color(5, 150, 105));
            } else {
                lblLiveVerdict.setForeground(new Color(220, 38, 38));
            }

            refreshHistoryAsync();
        });
    }

    @Override
    public void onScoreboardUpdated(List<ScoreboardRowDTO> scoreboard) {
        scoreboardPanel.updateScoreboard(scoreboard);
    }

    @Override
    public void onBroadcastMessage(String message) {
        SwingUtilities.invokeLater(() -> {
            JOptionPane.showMessageDialog(this, message, "[!] Thông Báo Hệ Thống Từ Giáo Viên", JOptionPane.INFORMATION_MESSAGE);
        });
    }

    @Override
    public void onConnectionStateChanged(boolean connected, String message) {
        SwingUtilities.invokeLater(() -> {
            styleConnectionChip(connected);
            if (!connected) {
                lblLiveVerdict.setText("× Mất kết nối: " + message);
                lblLiveVerdict.setForeground(new Color(220, 38, 38));
            }
        });
    }
}
