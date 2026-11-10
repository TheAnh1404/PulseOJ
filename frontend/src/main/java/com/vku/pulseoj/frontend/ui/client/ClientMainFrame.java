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
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.util.ArrayList;
import java.util.List;

/**
 * [TV3] Contestant Workspace Main Window.
 * 3-Column Split Interface:
 *   - Column 1: Problem Browser & Statement with Sample Tests
 *   - Column 2: Code Editor (RSyntaxTextArea with syntax highlighting)
 *   - Column 3: Live Incremental Judging Feed & Submission History
 * Includes Tab for Live ACM-ICPC Scoreboard.
 */
public class ClientMainFrame extends JFrame implements ClientUiListener {

    private final ClientBackendController controller;
    private final List<ProblemDTO> problemsList = new ArrayList<>();
    private ProblemDTO currentProblem;

    // Column 1: Problem Browser
    private final JComboBox<String> cbProblems = new JComboBox<>();
    private final JLabel lblProblemTitle = new JLabel("Select a problem");
    private final JLabel lblProblemLimits = new JLabel("Time: --- | Memory: ---");
    private final JTextArea txtDescription = new JTextArea();
    private final DefaultTableModel sampleTestsModel;
    private final JTable sampleTestsTable;

    // Column 2: Code Editor
    private final JComboBox<Language> cbLanguage = new JComboBox<>(Language.values());
    private final RSyntaxTextArea codeEditor = new RSyntaxTextArea();
    private final JButton btnSubmit = new JButton("Submit Solution");
    private final JButton btnResetCode = new JButton("Reset Template");

    // Column 3: Live Verdicts & Submission History
    private final JLabel lblLiveVerdict = new JLabel("Ready to submit.");
    private final JProgressBar pbJudging = new JProgressBar(0, 100);
    private final DefaultTableModel testProgressModel;
    private final JTable testProgressTable;
    private final DefaultTableModel historyModel;
    private final JTable historyTable;

    // Scoreboard Panel
    private final ScoreboardPanel scoreboardPanel;

    public ClientMainFrame(ClientBackendController controller) {
        super("PulseOJ Contestant Workspace - " +
              (controller.getCurrentContestant() != null ? controller.getCurrentContestant().getFullName() : "Contestant"));
        this.controller = controller;
        this.controller.setUiListener(this);

        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1240, 800);
        setMinimumSize(new Dimension(1000, 650));
        setLocationRelativeTo(null);

        // 1. Column 1: Problem Panel
        JPanel col1 = new JPanel(new BorderLayout(5, 5));
        col1.setBorder(BorderFactory.createTitledBorder("1. Problem Statement"));
        col1.setPreferredSize(new Dimension(360, 700));

        JPanel col1Header = new JPanel(new MigLayout("insets 5, gap 5, fillx", "[grow]", "[]"));
        col1Header.add(cbProblems, "growx, wrap");
        lblProblemTitle.setFont(lblProblemTitle.getFont().deriveFont(Font.BOLD, 14f));
        col1Header.add(lblProblemTitle, "wrap");
        lblProblemLimits.setForeground(Color.GRAY);
        col1Header.add(lblProblemLimits, "wrap");
        col1.add(col1Header, BorderLayout.NORTH);

        txtDescription.setEditable(false);
        txtDescription.setLineWrap(true);
        txtDescription.setWrapStyleWord(true);
        txtDescription.setFont(new Font("SansSerif", Font.PLAIN, 13));
        JScrollPane descScroll = new JScrollPane(txtDescription);

        String[] sampleCols = {"#", "Input", "Expected Output"};
        sampleTestsModel = new DefaultTableModel(sampleCols, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };
        sampleTestsTable = new JTable(sampleTestsModel);
        sampleTestsTable.setRowHeight(22);
        JScrollPane sampleScroll = new JScrollPane(sampleTestsTable);
        sampleScroll.setBorder(BorderFactory.createTitledBorder("Sample Test Cases"));
        sampleScroll.setPreferredSize(new Dimension(360, 160));

        JSplitPane col1Split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, descScroll, sampleScroll);
        col1Split.setResizeWeight(0.7);
        col1.add(col1Split, BorderLayout.CENTER);

        // 2. Column 2: Code Editor (RSyntaxTextArea)
        JPanel col2 = new JPanel(new BorderLayout(5, 5));
        col2.setBorder(BorderFactory.createTitledBorder("2. Source Code Editor"));
        col2.setPreferredSize(new Dimension(480, 700));

        JPanel editorToolbar = new JPanel(new MigLayout("insets 5, gap 8", "[][grow][]", "[]"));
        editorToolbar.add(new JLabel("Language:"));
        editorToolbar.add(cbLanguage);
        editorToolbar.add(btnResetCode);
        col2.add(editorToolbar, BorderLayout.NORTH);

        codeEditor.setSyntaxEditingStyle(SyntaxConstants.SYNTAX_STYLE_JAVA);
        codeEditor.setCodeFoldingEnabled(true);
        codeEditor.setFont(new Font("Consolas", Font.PLAIN, 14));
        col2.add(new RTextScrollPane(codeEditor), BorderLayout.CENTER);

        JPanel submitBar = new JPanel(new MigLayout("insets 8, gap 10, fillx", "[grow]", "[]"));
        btnSubmit.setFont(btnSubmit.getFont().deriveFont(Font.BOLD, 14f));
        btnSubmit.setBackground(new Color(46, 204, 113));
        btnSubmit.setForeground(Color.WHITE);
        btnSubmit.setPreferredSize(new Dimension(180, 36));
        submitBar.add(btnSubmit, "growx");
        col2.add(submitBar, BorderLayout.SOUTH);

        // 3. Column 3: Live Judging & History
        JPanel col3 = new JPanel(new BorderLayout(5, 5));
        col3.setBorder(BorderFactory.createTitledBorder("3. Live Judging & History"));
        col3.setPreferredSize(new Dimension(380, 700));

        JPanel liveStatusPanel = new JPanel(new MigLayout("insets 8, gap 5, fillx", "[grow]", "[]"));
        lblLiveVerdict.setFont(lblLiveVerdict.getFont().deriveFont(Font.BOLD, 13f));
        liveStatusPanel.add(lblLiveVerdict, "wrap");
        pbJudging.setStringPainted(true);
        pbJudging.setString("Idle");
        liveStatusPanel.add(pbJudging, "growx");
        col3.add(liveStatusPanel, BorderLayout.NORTH);

        String[] progCols = {"Test #", "Verdict", "Time (ms)"};
        testProgressModel = new DefaultTableModel(progCols, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };
        testProgressTable = new JTable(testProgressModel);
        testProgressTable.setRowHeight(22);
        setupVerdictCellRenderer(testProgressTable, 1);
        JScrollPane progScroll = new JScrollPane(testProgressTable);
        progScroll.setBorder(BorderFactory.createTitledBorder("Incremental Test Results (RMI Callback)"));

        String[] histCols = {"Sub ID", "Problem", "Verdict", "Time (ms)"};
        historyModel = new DefaultTableModel(histCols, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };
        historyTable = new JTable(historyModel);
        historyTable.setRowHeight(22);
        setupVerdictCellRenderer(historyTable, 2);
        JScrollPane histScroll = new JScrollPane(historyTable);
        histScroll.setBorder(BorderFactory.createTitledBorder("My Submission History"));

        JSplitPane col3Split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, progScroll, histScroll);
        col3Split.setResizeWeight(0.5);
        col3.add(col3Split, BorderLayout.CENTER);

        // Assemble 3 Columns with SplitPanes
        JSplitPane rightTwoCols = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, col2, col3);
        rightTwoCols.setResizeWeight(0.55);

        JSplitPane mainThreeCols = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, col1, rightTwoCols);
        mainThreeCols.setResizeWeight(0.3);

        // Main Tabbed Interface (Coding Workspace + Scoreboard)
        scoreboardPanel = new ScoreboardPanel(controller);

        JTabbedPane mainTabs = new JTabbedPane();
        mainTabs.setFont(mainTabs.getFont().deriveFont(Font.BOLD, 13f));
        mainTabs.addTab("Code Workspace (3 Columns)", mainThreeCols);
        mainTabs.addTab("Live ACM-ICPC Scoreboard", scoreboardPanel);

        setLayout(new BorderLayout());
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

    private void loadProblemsAsync() {
        controller.fetchProblemsAsync().thenAccept(list -> {
            SwingUtilities.invokeLater(() -> {
                problemsList.clear();
                problemsList.addAll(list);

                DefaultComboBoxModel<String> model = new DefaultComboBoxModel<>();
                for (ProblemDTO p : problemsList) {
                    model.addElement(p.getCode() + " - " + p.getTitle());
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
        lblProblemTitle.setText("[" + currentProblem.getCode() + "] " + currentProblem.getTitle());
        lblProblemLimits.setText(String.format("Time Limit: %d ms | Memory Limit: %d MB",
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
            JOptionPane.showMessageDialog(this, "Please select a problem first.", "Warning", JOptionPane.WARNING_MESSAGE);
            return;
        }

        String sourceCode = codeEditor.getText().trim();
        if (sourceCode.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Cannot submit empty source code.", "Warning", JOptionPane.WARNING_MESSAGE);
            return;
        }

        Language lang = (Language) cbLanguage.getSelectedItem();
        btnSubmit.setEnabled(false);
        testProgressModel.setRowCount(0);
        lblLiveVerdict.setText("Submitting to Master queue...");
        lblLiveVerdict.setForeground(new Color(243, 156, 18));
        pbJudging.setValue(0);
        pbJudging.setString("Queued...");

        controller.submitSolutionAsync(currentProblem, lang, sourceCode)
                .thenAccept(sub -> {
                    SwingUtilities.invokeLater(() -> {
                        lblLiveVerdict.setText(String.format("Sub #%d Queued. Awaiting Worker...", sub.getId()));
                        refreshHistoryAsync();
                    });
                })
                .exceptionally(ex -> {
                    SwingUtilities.invokeLater(() -> {
                        btnSubmit.setEnabled(true);
                        lblLiveVerdict.setText("Submission error: " + ex.getMessage());
                        lblLiveVerdict.setForeground(new Color(231, 76, 60));
                    });
                    return null;
                });
    }

    private void refreshHistoryAsync() {
        controller.fetchSubmissionHistoryAsync().thenAccept(list -> {
            SwingUtilities.invokeLater(() -> {
                historyModel.setRowCount(0);
                for (SubmissionDTO s : list) {
                    historyModel.addRow(new Object[]{
                            s.getId(), s.getProblemCode(), s.getVerdict().name(), s.getExecutionTimeMs()
                    });
                }
            });
        });
    }

    private void setupVerdictCellRenderer(JTable table, int colIndex) {
        table.getColumnModel().getColumn(colIndex).setCellRenderer(new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
                Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
                if (value != null) {
                    String str = value.toString();
                    if (str.startsWith("AC") || str.contains("Accepted")) {
                        c.setForeground(new Color(40, 180, 99));
                        c.setFont(c.getFont().deriveFont(Font.BOLD));
                    } else if (str.startsWith("WA") || str.startsWith("RE") || str.startsWith("TLE") || str.startsWith("CE")) {
                        c.setForeground(new Color(231, 76, 60));
                        c.setFont(c.getFont().deriveFont(Font.BOLD));
                    } else {
                        c.setForeground(new Color(243, 156, 18));
                    }
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
            int percent = (int) (((double) result.getCurrentTestIndex() / result.getTotalTests()) * 100);
            pbJudging.setValue(percent);
            pbJudging.setString(String.format("Running Test %d/%d (%d%%)", result.getCurrentTestIndex(), result.getTotalTests(), percent));

            testProgressModel.addRow(new Object[]{
                    "Test #" + result.getCurrentTestIndex(),
                    result.getVerdict().name(),
                    result.getExecutionTimeMs()
            });

            lblLiveVerdict.setText(String.format("Sub #%d Test %d/%d -> %s",
                    result.getSubmissionId(), result.getCurrentTestIndex(), result.getTotalTests(), result.getVerdict()));
        });
    }

    @Override
    public void onFinalVerdict(SubmissionDTO submission) {
        SwingUtilities.invokeLater(() -> {
            btnSubmit.setEnabled(true);
            pbJudging.setValue(100);
            pbJudging.setString("Final: " + submission.getVerdict().name());

            Verdict v = submission.getVerdict();
            lblLiveVerdict.setText(String.format("Sub #%d Result: %s (Time: %d ms)",
                    submission.getId(), v.name(), submission.getExecutionTimeMs()));

            if (v == Verdict.AC) {
                lblLiveVerdict.setForeground(new Color(40, 180, 99));
            } else {
                lblLiveVerdict.setForeground(new Color(231, 76, 60));
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
            JOptionPane.showMessageDialog(this, message, "System Announcement", JOptionPane.INFORMATION_MESSAGE);
        });
    }

    @Override
    public void onConnectionStateChanged(boolean connected, String message) {
        SwingUtilities.invokeLater(() -> {
            if (!connected) {
                lblLiveVerdict.setText("Disconnected: " + message);
                lblLiveVerdict.setForeground(new Color(231, 76, 60));
            }
        });
    }
}
