package com.vku.pulseoj.frontend.ui.master;

import com.vku.pulseoj.backend.master.MasterBackendController;
import com.vku.pulseoj.common.dto.ProblemDTO;
import com.vku.pulseoj.common.dto.TestCaseDTO;
import net.miginfocom.swing.MigLayout;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * [TV1] Problem & Hidden Test Case Management Panel.
 * CRUD for problems, setting time/memory limits, and creating test cases.
 */
public class ProblemManagementPanel extends JPanel {

    private final MasterBackendController controller;

    private final DefaultTableModel problemsModel;
    private final JTable problemsTable;

    private final DefaultTableModel testCasesModel;
    private final JTable testCasesTable;

    private final JTextArea txtDescription = new JTextArea();
    private final JLabel lblProblemDetails = new JLabel("Select a problem to view details");

    private final JButton btnNewProblem = new JButton("Create New Problem");
    private final JButton btnAddTestCase = new JButton("Add Test Case to Selected");
    private final JButton btnRefresh = new JButton("Refresh");

    private ProblemDTO currentSelectedProblem;

    public ProblemManagementPanel(MasterBackendController controller) {
        this.controller = controller;
        setLayout(new BorderLayout(10, 10));
        setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // 1. Problems Table (Left)
        String[] problemCols = {"ID", "Code", "Title", "TL (ms)", "ML (MB)"};
        problemsModel = new DefaultTableModel(problemCols, 0) {
            @Override
            public boolean isCellEditable(int r, int c) { return false; }
        };
        problemsTable = new JTable(problemsModel);
        problemsTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        problemsTable.setRowHeight(24);

        JPanel leftPanel = new JPanel(new BorderLayout(5, 5));
        leftPanel.setBorder(BorderFactory.createTitledBorder("Problem Repository"));
        leftPanel.add(new JScrollPane(problemsTable), BorderLayout.CENTER);
        leftPanel.setPreferredSize(new Dimension(380, 500));

        // 2. Right Panel (Problem details & Test Cases)
        JPanel rightPanel = new JPanel(new BorderLayout(5, 5));
        rightPanel.setBorder(BorderFactory.createTitledBorder("Problem Details & Test Cases"));

        // Description view
        txtDescription.setEditable(false);
        txtDescription.setLineWrap(true);
        txtDescription.setWrapStyleWord(true);
        JScrollPane descScroll = new JScrollPane(txtDescription);
        descScroll.setPreferredSize(new Dimension(400, 150));

        JPanel detailHeader = new JPanel(new BorderLayout());
        detailHeader.add(lblProblemDetails, BorderLayout.NORTH);
        detailHeader.add(descScroll, BorderLayout.CENTER);

        // Test Cases Table
        String[] testCols = {"#", "Sample?", "Input Snippet", "Expected Output Snippet"};
        testCasesModel = new DefaultTableModel(testCols, 0) {
            @Override
            public boolean isCellEditable(int r, int c) { return false; }
        };
        testCasesTable = new JTable(testCasesModel);
        testCasesTable.setRowHeight(22);

        JPanel testCasesPanel = new JPanel(new BorderLayout());
        testCasesPanel.setBorder(BorderFactory.createTitledBorder("Test Cases (Hidden & Sample)"));
        testCasesPanel.add(new JScrollPane(testCasesTable), BorderLayout.CENTER);

        JSplitPane rightSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT, detailHeader, testCasesPanel);
        rightSplit.setResizeWeight(0.4);
        rightPanel.add(rightSplit, BorderLayout.CENTER);

        // Split Left and Right
        JSplitPane mainSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, leftPanel, rightPanel);
        mainSplit.setResizeWeight(0.35);
        add(mainSplit, BorderLayout.CENTER);

        // 3. Bottom Toolbar
        JPanel bottomBar = new JPanel(new MigLayout("insets 5, gap 10"));
        bottomBar.add(btnNewProblem);
        bottomBar.add(btnAddTestCase);
        bottomBar.add(btnRefresh);
        add(bottomBar, BorderLayout.SOUTH);

        // Events
        problemsTable.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                onProblemSelected();
            }
        });

        btnNewProblem.addActionListener(e -> showCreateProblemDialog());
        btnAddTestCase.addActionListener(e -> showAddTestCaseDialog());
        btnRefresh.addActionListener(e -> refreshProblemsAsync());

        // Initial Load
        refreshProblemsAsync();
    }

    public void refreshProblemsAsync() {
        CompletableFuture.runAsync(() -> {
            List<ProblemDTO> list = controller.getAllProblems();
            SwingUtilities.invokeLater(() -> {
                problemsModel.setRowCount(0);
                for (ProblemDTO p : list) {
                    problemsModel.addRow(new Object[]{
                            p.getId(), p.getCode(), p.getTitle(), p.getTimeLimitMs(), p.getMemoryLimitMb()
                    });
                }
            });
        });
    }

    private void onProblemSelected() {
        int row = problemsTable.getSelectedRow();
        if (row < 0) return;

        int id = (int) problemsModel.getValueAt(row, 0);
        CompletableFuture.runAsync(() -> {
            List<TestCaseDTO> tests = controller.getTestCases(id, true);
            SwingUtilities.invokeLater(() -> {
                String code = (String) problemsModel.getValueAt(row, 1);
                String title = (String) problemsModel.getValueAt(row, 2);
                int tl = (int) problemsModel.getValueAt(row, 3);
                int ml = (int) problemsModel.getValueAt(row, 4);

                currentSelectedProblem = new ProblemDTO(id, code, title, "", tl, ml);
                lblProblemDetails.setText(String.format("[%s] %s | Time: %d ms | Mem: %d MB", code, title, tl, ml));

                testCasesModel.setRowCount(0);
                for (TestCaseDTO tc : tests) {
                    testCasesModel.addRow(new Object[]{
                            tc.getTestOrder(),
                            tc.isSample() ? "YES" : "NO (Hidden)",
                            tc.getInputData() != null ? tc.getInputData().replace("\n", " ").trim() : "",
                            tc.getExpectedOutput() != null ? tc.getExpectedOutput().replace("\n", " ").trim() : ""
                    });
                }
            });
        });
    }

    private void showCreateProblemDialog() {
        JDialog dialog = new JDialog(SwingUtilities.getWindowAncestor(this), "Create New Problem", JDialog.ModalityType.APPLICATION_MODAL);
        dialog.setLayout(new MigLayout("insets 15, gap 10", "[][grow]", "[]"));

        JTextField txtCode = new JTextField(15);
        JTextField txtTitle = new JTextField(25);
        JTextField txtTl = new JTextField("1000", 10);
        JTextField txtMl = new JTextField("256", 10);
        JTextArea txtDesc = new JTextArea(6, 30);
        txtDesc.setLineWrap(true);

        dialog.add(new JLabel("Problem Code:")); dialog.add(txtCode, "wrap");
        dialog.add(new JLabel("Title:")); dialog.add(txtTitle, "wrap");
        dialog.add(new JLabel("Time Limit (ms):")); dialog.add(txtTl, "wrap");
        dialog.add(new JLabel("Memory Limit (MB):")); dialog.add(txtMl, "wrap");
        dialog.add(new JLabel("Description:"), "top"); dialog.add(new JScrollPane(txtDesc), "wrap");

        JButton btnSave = new JButton("Save Problem");
        btnSave.addActionListener(e -> {
            try {
                ProblemDTO p = new ProblemDTO(
                        null,
                        txtCode.getText().trim().toUpperCase(),
                        txtTitle.getText().trim(),
                        txtDesc.getText(),
                        Integer.parseInt(txtTl.getText().trim()),
                        Integer.parseInt(txtMl.getText().trim())
                );
                controller.saveProblem(p);
                dialog.dispose();
                refreshProblemsAsync();
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(dialog, "Error saving problem: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            }
        });

        dialog.add(btnSave, "span 2, align right");
        dialog.pack();
        dialog.setLocationRelativeTo(this);
        dialog.setVisible(true);
    }

    private void showAddTestCaseDialog() {
        if (currentSelectedProblem == null) {
            JOptionPane.showMessageDialog(this, "Select a problem first.", "Note", JOptionPane.WARNING_MESSAGE);
            return;
        }

        JDialog dialog = new JDialog(SwingUtilities.getWindowAncestor(this), "Add Test Case to " + currentSelectedProblem.getCode(), JDialog.ModalityType.APPLICATION_MODAL);
        dialog.setLayout(new MigLayout("insets 15, gap 10", "[][grow]", "[]"));

        JTextField txtOrder = new JTextField(String.valueOf(testCasesModel.getRowCount() + 1), 6);
        JCheckBox chkSample = new JCheckBox("Is Sample Test Case (Visible to contestants)");
        JTextArea txtInput = new JTextArea(4, 30);
        JTextArea txtOutput = new JTextArea(4, 30);

        dialog.add(new JLabel("Test Order:")); dialog.add(txtOrder, "wrap");
        dialog.add(chkSample, "span 2, wrap");
        dialog.add(new JLabel("Input:"), "top"); dialog.add(new JScrollPane(txtInput), "wrap");
        dialog.add(new JLabel("Expected Output:"), "top"); dialog.add(new JScrollPane(txtOutput), "wrap");

        JButton btnSave = new JButton("Add Test Case");
        btnSave.addActionListener(e -> {
            try {
                TestCaseDTO tc = new TestCaseDTO(
                        null,
                        currentSelectedProblem.getId(),
                        Integer.parseInt(txtOrder.getText().trim()),
                        txtInput.getText(),
                        txtOutput.getText(),
                        chkSample.isSelected()
                );
                controller.addTestCase(tc);
                dialog.dispose();
                onProblemSelected();
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(dialog, "Error: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            }
        });

        dialog.add(btnSave, "span 2, align right");
        dialog.pack();
        dialog.setLocationRelativeTo(this);
        dialog.setVisible(true);
    }
}
