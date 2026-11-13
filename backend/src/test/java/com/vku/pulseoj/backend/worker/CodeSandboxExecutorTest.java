package com.vku.pulseoj.backend.worker;

import com.vku.pulseoj.common.dto.JudgeResultDTO;
import com.vku.pulseoj.common.dto.ProblemDTO;
import com.vku.pulseoj.common.dto.SubmissionDTO;
import com.vku.pulseoj.common.dto.TestCaseDTO;
import com.vku.pulseoj.common.enums.Language;
import com.vku.pulseoj.common.enums.Verdict;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * [TV2] Unit & Integration tests for CodeSandboxExecutor.
 * Verifies isolated compilation, execution, verdict classification (AC, WA, CE, RE, TLE),
 * early-break ICPC rule, and Demo Slow Mode delay.
 */
public class CodeSandboxExecutorTest {

    private CodeSandboxExecutor executor;
    private ProblemDTO sampleProblem;
    private List<TestCaseDTO> twoSumTests;

    @BeforeEach
    void setUp() {
        executor = new CodeSandboxExecutor("Worker-Test-01");
        sampleProblem = new ProblemDTO(1, "TWO_SUM", "Two Sum", "Compute A + B", 1000, 256);
        twoSumTests = List.of(
                new TestCaseDTO(1, 1, 1, "3 5\n", "8\n", true),
                new TestCaseDTO(2, 1, 2, "100 250\n", "350\n", false)
        );
    }

    @Test
    @DisplayName("1. Valid Java solution passes all test cases with AC and emits incremental results")
    void testAcceptedVerdictAndIncrementalProgress() {
        String validJava = """
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

        SubmissionDTO sub = new SubmissionDTO(101, 1, "student01", 1, "TWO_SUM", Language.JAVA, validJava);
        List<JudgeResultDTO> progressList = new ArrayList<>();

        SubmissionDTO completed = executor.executeSubmission(sub, sampleProblem, twoSumTests, progressList::add);

        assertEquals(Verdict.AC, completed.getVerdict(), "Overall verdict must be AC");
        assertEquals(2, progressList.size(), "Must emit 2 incremental test progress events");
        assertEquals(Verdict.AC, progressList.get(0).getVerdict());
        assertFalse(progressList.get(0).isFinalVerdict());
        assertEquals(Verdict.AC, progressList.get(1).getVerdict());
        assertTrue(progressList.get(1).isFinalVerdict());
    }

    @Test
    @DisplayName("2. Wrong Answer (WA) triggers early break on first failing test case (ICPC rule)")
    void testWrongAnswerEarlyBreak() {
        String wrongJava = """
                import java.util.Scanner;
                public class Main {
                    public static void main(String[] args) {
                        Scanner sc = new Scanner(System.in);
                        long a = sc.nextLong();
                        long b = sc.nextLong();
                        System.out.println(a - b);
                    }
                }
                """;

        SubmissionDTO sub = new SubmissionDTO(102, 1, "student01", 1, "TWO_SUM", Language.JAVA, wrongJava);
        List<JudgeResultDTO> progressList = new ArrayList<>();

        SubmissionDTO completed = executor.executeSubmission(sub, sampleProblem, twoSumTests, progressList::add);

        assertEquals(Verdict.WA, completed.getVerdict(), "Overall verdict must be WA");
        assertEquals(1, progressList.size(), "Should stop immediately after failing Test #1");
        assertTrue(progressList.get(0).isFinalVerdict());
    }

    @Test
    @DisplayName("3. Syntax error yields Compilation Error (CE) with non-empty compiler output")
    void testCompilationErrorCapture() {
        String brokenJava = """
                public class Main {
                    public static void main(String[] args) {
                        System.out.println("Missing semicolon")
                    }
                }
                """;

        SubmissionDTO sub = new SubmissionDTO(103, 1, "student01", 1, "TWO_SUM", Language.JAVA, brokenJava);
        List<JudgeResultDTO> progressList = new ArrayList<>();

        SubmissionDTO completed = executor.executeSubmission(sub, sampleProblem, twoSumTests, progressList::add);

        assertEquals(Verdict.CE, completed.getVerdict(), "Broken syntax must yield Verdict.CE");
        assertNotNull(completed.getCompilerOutput());
        assertFalse(completed.getCompilerOutput().isBlank(), "Compiler error details must be captured");
        assertEquals(1, progressList.size());
        assertEquals(Verdict.CE, progressList.get(0).getVerdict());
    }

    @Test
    @DisplayName("4. Uncaught runtime exception yields Runtime Error (RE)")
    void testRuntimeErrorDetection() {
        String crashingJava = """
                public class Main {
                    public static void main(String[] args) {
                        int x = 10 / 0;
                        System.out.println(x);
                    }
                }
                """;

        SubmissionDTO sub = new SubmissionDTO(104, 1, "student01", 1, "TWO_SUM", Language.JAVA, crashingJava);
        List<JudgeResultDTO> progressList = new ArrayList<>();

        SubmissionDTO completed = executor.executeSubmission(sub, sampleProblem, twoSumTests, progressList::add);

        assertEquals(Verdict.RE, completed.getVerdict(), "Division by zero must yield Verdict.RE");
        assertEquals(1, progressList.size());
        assertEquals(Verdict.RE, progressList.get(0).getVerdict());
    }

    @Test
    @DisplayName("5. Infinite loop is forcibly killed and yields Time Limit Exceeded (TLE)")
    void testTimeLimitExceededForcibleKill() {
        String infiniteLoopJava = """
                public class Main {
                    public static void main(String[] args) {
                        while (true) {
                            // Busy loop
                        }
                    }
                }
                """;

        ProblemDTO fastProblem = new ProblemDTO(2, "FAST_TEST", "Fast Test", "Strict time limit", 300, 256);
        SubmissionDTO sub = new SubmissionDTO(105, 1, "student01", 2, "FAST_TEST", Language.JAVA, infiniteLoopJava);
        List<JudgeResultDTO> progressList = new ArrayList<>();

        SubmissionDTO completed = executor.executeSubmission(sub, fastProblem, twoSumTests, progressList::add);

        assertEquals(Verdict.TLE, completed.getVerdict(), "Infinite loop exceeding 300ms must yield Verdict.TLE");
        assertEquals(1, progressList.size());
        assertEquals(Verdict.TLE, progressList.get(0).getVerdict());
    }

    @Test
    @DisplayName("6. Demo Slow Mode applies configurable delay per test case")
    void testDemoSlowModeDelay() {
        String validJava = """
                import java.util.Scanner;
                public class Main {
                    public static void main(String[] args) {
                        Scanner sc = new Scanner(System.in);
                        System.out.println(sc.nextLong() + sc.nextLong());
                    }
                }
                """;

        executor.setDemoSlowMode(true);
        executor.setDemoDelayMs(150L);

        SubmissionDTO sub = new SubmissionDTO(106, 1, "student01", 1, "TWO_SUM", Language.JAVA, validJava);
        long start = System.currentTimeMillis();
        SubmissionDTO completed = executor.executeSubmission(sub, sampleProblem, twoSumTests, r -> {});
        long elapsed = System.currentTimeMillis() - start;

        assertEquals(Verdict.AC, completed.getVerdict());
        assertTrue(elapsed >= 300L, "2 test cases with 150ms demo delay each must take at least 300ms");
    }
}
