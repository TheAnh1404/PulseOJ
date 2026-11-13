package com.vku.pulseoj.backend.worker;

import com.vku.pulseoj.common.dto.JudgeResultDTO;
import com.vku.pulseoj.common.dto.ProblemDTO;
import com.vku.pulseoj.common.dto.SubmissionDTO;
import com.vku.pulseoj.common.dto.TestCaseDTO;
import com.vku.pulseoj.common.enums.Language;
import com.vku.pulseoj.common.enums.Verdict;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * [TV2] ProcessBuilder Sandbox Execution Engine.
 * Compiles and isolates execution of Java, C++, and Python code with strict time limit enforcement.
 */
public class CodeSandboxExecutor {

    private static final Logger logger = LoggerFactory.getLogger(CodeSandboxExecutor.class);

    private final String workerId;
    private Consumer<String> consoleLogger;
    private java.util.function.BiConsumer<Integer, Integer> testStartListener;
    private volatile boolean demoSlowMode = false;
    private volatile long demoDelayMs = 400L;

    public CodeSandboxExecutor(String workerId) {
        this.workerId = workerId;
    }

    public void setConsoleLogger(Consumer<String> consoleLogger) {
        this.consoleLogger = consoleLogger;
    }

    public void setTestStartListener(java.util.function.BiConsumer<Integer, Integer> testStartListener) {
        this.testStartListener = testStartListener;
    }

    public void setDemoSlowMode(boolean demoSlowMode) {
        this.demoSlowMode = demoSlowMode;
        log("Chế độ Demo Chậm (400ms/test): " + (demoSlowMode ? "BẬT" : "TẮT"));
    }

    public boolean isDemoSlowMode() {
        return demoSlowMode;
    }

    public void setDemoDelayMs(long demoDelayMs) {
        this.demoDelayMs = Math.max(0L, demoDelayMs);
    }

    private void log(String msg) {
        if (consoleLogger != null) {
            consoleLogger.accept(msg);
        }
        logger.info("[Sandbox-{}] {}", workerId, msg);
    }

    /**
     * Executes the submission through all test cases, emitting incremental results.
     */
    public SubmissionDTO executeSubmission(SubmissionDTO submission, ProblemDTO problem,
                                          List<TestCaseDTO> testCases,
                                          Consumer<JudgeResultDTO> onProgress) {
        Path sandboxDir = null;
        try {
            sandboxDir = Files.createTempDirectory("pulseoj_sub_" + submission.getId() + "_");
            log("Created sandbox directory: " + sandboxDir.toAbsolutePath());

            // 1. Write source file
            Language lang = submission.getLanguage();
            String fileName = lang.getFullSourceFileName();
            Path sourceFile = sandboxDir.resolve(fileName);
            Files.writeString(sourceFile, submission.getSourceCode(), StandardCharsets.UTF_8);
            log("Wrote source code to: " + fileName);

            // 2. Compilation step (if required)
            String compileError = compileCode(lang, sandboxDir, fileName);
            if (compileError != null) {
                log("Compilation Error (CE):\n" + compileError);
                submission.setVerdict(Verdict.CE);
                submission.setCompilerOutput(compileError);
                submission.setExecutionTimeMs(0);
                submission.setMemoryUsedKb(0);

                JudgeResultDTO ceResult = new JudgeResultDTO(
                        submission.getId(), Verdict.CE, 0, testCases.size(), 0, 0, workerId, true
                );
                ceResult.setErrorMessage(compileError);
                onProgress.accept(ceResult);
                return submission;
            }

            log("Compilation successful. Starting test case evaluation...");

            long maxExecTime = 0;
            long maxMemoryKb = 0;
            Verdict overallVerdict = Verdict.AC;
            int timeLimitMs = (problem != null && problem.getTimeLimitMs() > 0) ? problem.getTimeLimitMs() : 1000;

            // 3. Run test cases
            for (int i = 0; i < testCases.size(); i++) {
                if (Thread.currentThread().isInterrupted()) {
                    throw new IllegalStateException("Sandbox execution interrupted");
                }
                TestCaseDTO tc = testCases.get(i);
                int testIndex = i + 1;
                log(String.format("Running Test #%d/%d...", testIndex, testCases.size()));
                if (testStartListener != null) {
                    testStartListener.accept(testIndex, testCases.size());
                }

                if (demoSlowMode && demoDelayMs > 0) {
                    try {
                        Thread.sleep(demoDelayMs);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new IllegalStateException("Sandbox interrupted due to worker shutdown or crash", ie);
                    }
                }

                TestRunResult runResult = runSingleTest(lang, sandboxDir, tc.getInputData(), timeLimitMs);
                maxExecTime = Math.max(maxExecTime, runResult.executionTimeMs);
                maxMemoryKb = Math.max(maxMemoryKb, runResult.memoryUsedKb);

                Verdict testVerdict;
                if (runResult.timedOut) {
                    testVerdict = Verdict.TLE;
                    log(String.format("Test #%d: TLE (exceeded %d ms)", testIndex, timeLimitMs));
                } else if (runResult.exitCode != 0) {
                    testVerdict = Verdict.RE;
                    log(String.format("Test #%d: RE (exit code %d: %s)", testIndex, runResult.exitCode, runResult.stderr));
                } else {
                    boolean matched = compareOutputs(runResult.stdout, tc.getExpectedOutput());
                    testVerdict = matched ? Verdict.AC : Verdict.WA;
                    log(String.format("Test #%d: %s (time: %d ms)", testIndex, testVerdict, runResult.executionTimeMs));
                }

                boolean isLast = (i == testCases.size() - 1) || (testVerdict != Verdict.AC);

                JudgeResultDTO progress = new JudgeResultDTO(
                        submission.getId(), testVerdict, testIndex, testCases.size(),
                        runResult.executionTimeMs, runResult.memoryUsedKb, workerId, isLast
                );
                progress.setTestInputSnippet(truncate(tc.getInputData(), 80));
                progress.setExpectedSnippet(truncate(tc.getExpectedOutput(), 80));
                progress.setActualOutputSnippet(truncate(runResult.stdout, 80));
                progress.setErrorMessage(runResult.stderr);

                onProgress.accept(progress);

                if (testVerdict != Verdict.AC) {
                    overallVerdict = testVerdict;
                    break; // Early break on first failing test (ICPC rule)
                }
            }

            submission.setVerdict(overallVerdict);
            submission.setExecutionTimeMs(maxExecTime);
            submission.setMemoryUsedKb(maxMemoryKb);
            submission.setWorkerId(workerId);

        } catch (Exception e) {
            log("Execution failed with internal exception: " + e.getMessage());
            submission.setVerdict(Verdict.RE);
            submission.setCompilerOutput("Internal Sandbox Error: " + e.getMessage());
        } finally {
            cleanupDirectory(sandboxDir);
        }

        return submission;
    }

    private String compileCode(Language lang, Path dir, String fileName) {
        try {
            ProcessBuilder pb;
            switch (lang) {
                case JAVA:
                    pb = new ProcessBuilder("javac", fileName);
                    break;
                case CPP:
                    String exeName = System.getProperty("os.name").toLowerCase().contains("win") ? "solution.exe" : "solution";
                    pb = new ProcessBuilder("g++", "-O2", fileName, "-o", exeName);
                    break;
                case PYTHON:
                    // Python syntax verification
                    pb = new ProcessBuilder("python", "-m", "py_compile", fileName);
                    break;
                default:
                    return "Unsupported language";
            }

            pb.directory(dir.toFile());
            Process p = pb.start();
            boolean finished = p.waitFor(15, TimeUnit.SECONDS);

            if (!finished) {
                p.destroyForcibly();
                return "Compilation Timed Out (exceeded 15s)";
            }

            if (p.exitValue() != 0) {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(p.getErrorStream()))) {
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        sb.append(line).append("\n");
                    }
                    return sb.toString().trim();
                }
            }
            return null; // OK
        } catch (Exception e) {
            return "Compiler invocation error: " + e.getMessage();
        }
    }

    private TestRunResult runSingleTest(Language lang, Path dir, String inputData, int timeLimitMs) {
        ProcessBuilder pb;
        switch (lang) {
            case JAVA:
                pb = new ProcessBuilder("java", "-Xmx256m", "Main");
                break;
            case CPP:
                String exeName = System.getProperty("os.name").toLowerCase().contains("win") ? "./solution.exe" : "./solution";
                pb = new ProcessBuilder(exeName);
                break;
            case PYTHON:
                pb = new ProcessBuilder("python", "solution.py");
                break;
            default:
                return new TestRunResult(false, -1, "", "Unsupported language", 0, 0);
        }

        pb.directory(dir.toFile());

        long startTime = System.currentTimeMillis();
        try {
            Process process = pb.start();

            // Feed input data to stdin
            if (inputData != null && !inputData.isEmpty()) {
                try (OutputStream os = process.getOutputStream()) {
                    os.write(inputData.getBytes(StandardCharsets.UTF_8));
                    os.flush();
                }
            }

            // Wait for completion with timeout limit
            boolean completedInTime = process.waitFor(timeLimitMs + 500, TimeUnit.MILLISECONDS);
            long elapsed = System.currentTimeMillis() - startTime;

            if (!completedInTime || elapsed > timeLimitMs) {
                process.destroyForcibly();
                return new TestRunResult(true, -1, "", "Time Limit Exceeded", elapsed, 0);
            }

            String stdout = readStream(process.getInputStream());
            String stderr = readStream(process.getErrorStream());
            int exitCode = process.exitValue();

            return new TestRunResult(false, exitCode, stdout, stderr, elapsed, 1024); // estimate 1MB RAM
        } catch (Exception e) {
            return new TestRunResult(false, -1, "", e.getMessage(), 0, 0);
        }
    }

    private String readStream(java.io.InputStream is) throws Exception {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append("\n");
            }
            return sb.toString();
        }
    }

    private boolean compareOutputs(String actual, String expected) {
        if (actual == null) actual = "";
        if (expected == null) expected = "";

        // Normalize carriage returns and trailing whitespace
        String act = actual.replace("\r\n", "\n").replaceAll("\\s+$", "");
        String exp = expected.replace("\r\n", "\n").replaceAll("\\s+$", "");
        return act.equals(exp);
    }

    private String truncate(String text, int maxLen) {
        if (text == null) return "";
        String s = text.trim();
        return s.length() <= maxLen ? s : s.substring(0, maxLen) + "...";
    }

    private void cleanupDirectory(Path dir) {
        if (dir == null || !Files.exists(dir)) return;
        try {
            Files.walk(dir)
                    .sorted(Comparator.reverseOrder())
                    .map(Path::toFile)
                    .forEach(File::delete);
        } catch (Exception ignored) {}
    }

    private record TestRunResult(boolean timedOut, int exitCode, String stdout, String stderr,
                                 long executionTimeMs, long memoryUsedKb) {}
}
