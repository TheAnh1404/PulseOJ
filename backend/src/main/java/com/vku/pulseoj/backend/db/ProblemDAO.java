package com.vku.pulseoj.backend.db;

import com.vku.pulseoj.common.dto.ProblemDTO;
import com.vku.pulseoj.common.dto.TestCaseDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * [TV1] Data Access Object for Contest Problems and Hidden Test Cases.
 * Interacts with Neon PostgreSQL with robust in-memory mock fallback.
 */
public class ProblemDAO {

    private static final Logger logger = LoggerFactory.getLogger(ProblemDAO.class);

    private static final Map<Integer, ProblemDTO> MOCK_PROBLEMS = new ConcurrentHashMap<>();
    private static final Map<String, ProblemDTO> MOCK_PROBLEMS_BY_CODE = new ConcurrentHashMap<>();
    private static final Map<Integer, List<TestCaseDTO>> MOCK_TEST_CASES = new ConcurrentHashMap<>();
    private static final AtomicInteger PROBLEM_ID_GEN = new AtomicInteger(10);
    private static final AtomicInteger TEST_CASE_ID_GEN = new AtomicInteger(100);

    static {
        seedInitialMockData();
    }

    private static void seedInitialMockData() {
        // Problem 1: TWO_SUM
        int p1Id = 1;
        ProblemDTO p1 = new ProblemDTO(p1Id, "TWO_SUM", "Two Sum Problem",
                "Cho hai so nguyen A va B cach nhau boi dau cach tren mot dong. Hay in ra tong A + B tren mot dong duy nhat.\n\n" +
                "Input:\nDong duy nhat chua 2 so nguyen A, B (-10^9 <= A, B <= 10^9).\n\n" +
                "Output:\nIn ra mot so nguyen duy nhat la tong A + B.", 1000, 256);
        MOCK_PROBLEMS.put(p1Id, p1);
        MOCK_PROBLEMS_BY_CODE.put("TWO_SUM", p1);

        List<TestCaseDTO> p1Tests = new ArrayList<>();
        p1Tests.add(new TestCaseDTO(1, p1Id, 1, "2 3", "5", true));
        p1Tests.add(new TestCaseDTO(2, p1Id, 2, "-10 10", "0", false));
        p1Tests.add(new TestCaseDTO(3, p1Id, 3, "1000000 2000000", "3000000", false));
        MOCK_TEST_CASES.put(p1Id, p1Tests);
        p1.setTestCases(p1Tests);

        // Problem 2: FACTORIAL
        int p2Id = 2;
        ProblemDTO p2 = new ProblemDTO(p2Id, "FACTORIAL", "Tich Giai Thua N!",
                "Cho so nguyen N (0 <= N <= 12). Tinh N giai thua (N!).\n\n" +
                "Input:\nMot so nguyen N duy nhat.\n\n" +
                "Output:\nGia tri N!.", 1000, 256);
        MOCK_PROBLEMS.put(p2Id, p2);
        MOCK_PROBLEMS_BY_CODE.put("FACTORIAL", p2);

        List<TestCaseDTO> p2Tests = new ArrayList<>();
        p2Tests.add(new TestCaseDTO(4, p2Id, 1, "5", "120", true));
        p2Tests.add(new TestCaseDTO(5, p2Id, 2, "0", "1", false));
        p2Tests.add(new TestCaseDTO(6, p2Id, 3, "10", "3628800", false));
        MOCK_TEST_CASES.put(p2Id, p2Tests);
        p2.setTestCases(p2Tests);
    }

    /**
     * Retrieves all problems with only their sample test cases populated.
     */
    public List<ProblemDTO> getAllProblems() {
        if (NeonDataSourceConfig.isMockMode()) {
            List<ProblemDTO> list = new ArrayList<>();
            for (ProblemDTO p : MOCK_PROBLEMS.values()) {
                ProblemDTO copy = shallowCopy(p);
                copy.setTestCases(getTestCasesForProblem(p.getId(), false));
                list.add(copy);
            }
            list.sort(java.util.Comparator.comparingInt(ProblemDTO::getId));
            return list;
        }

        List<ProblemDTO> list = new ArrayList<>();
        String sql = "SELECT id, code, title, description, time_limit_ms, memory_limit_mb FROM problems ORDER BY id ASC";
        try (Connection conn = NeonDataSourceConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                ProblemDTO p = new ProblemDTO(
                        rs.getInt("id"),
                        rs.getString("code"),
                        rs.getString("title"),
                        rs.getString("description"),
                        rs.getInt("time_limit_ms"),
                        rs.getInt("memory_limit_mb")
                );
                p.setTestCases(getTestCasesForProblem(p.getId(), false));
                list.add(p);
            }
        } catch (SQLException e) {
            logger.error("Error retrieving problems from DB: {}", e.getMessage());
        }
        return list;
    }

    /**
     * Retrieves a single problem with sample tests or full hidden test cases.
     */
    public ProblemDTO getProblemById(int problemId, boolean includeHidden) {
        if (NeonDataSourceConfig.isMockMode()) {
            ProblemDTO p = MOCK_PROBLEMS.get(problemId);
            if (p != null) {
                ProblemDTO copy = shallowCopy(p);
                copy.setTestCases(getTestCasesForProblem(problemId, includeHidden));
                return copy;
            }
            return null;
        }

        String sql = "SELECT id, code, title, description, time_limit_ms, memory_limit_mb FROM problems WHERE id = ?";
        try (Connection conn = NeonDataSourceConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, problemId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    ProblemDTO p = new ProblemDTO(
                            rs.getInt("id"),
                            rs.getString("code"),
                            rs.getString("title"),
                            rs.getString("description"),
                            rs.getInt("time_limit_ms"),
                            rs.getInt("memory_limit_mb")
                    );
                    p.setTestCases(getTestCasesForProblem(problemId, includeHidden));
                    return p;
                }
            }
        } catch (SQLException e) {
            logger.error("Error fetching problem {}: {}", problemId, e.getMessage());
        }
        return null;
    }

    public List<TestCaseDTO> getTestCasesForProblem(int problemId, boolean includeHidden) {
        if (NeonDataSourceConfig.isMockMode()) {
            List<TestCaseDTO> all = MOCK_TEST_CASES.getOrDefault(problemId, new ArrayList<>());
            List<TestCaseDTO> result = new ArrayList<>();
            for (TestCaseDTO tc : all) {
                if (includeHidden || tc.isSample()) {
                    result.add(tc);
                }
            }
            return result;
        }

        List<TestCaseDTO> list = new ArrayList<>();
        String sql = includeHidden
                ? "SELECT id, problem_id, test_order, input_data, expected_output, is_sample FROM test_cases WHERE problem_id = ? ORDER BY test_order ASC"
                : "SELECT id, problem_id, test_order, input_data, expected_output, is_sample FROM test_cases WHERE problem_id = ? AND is_sample = TRUE ORDER BY test_order ASC";

        try (Connection conn = NeonDataSourceConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, problemId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(new TestCaseDTO(
                            rs.getInt("id"),
                            rs.getInt("problem_id"),
                            rs.getInt("test_order"),
                            rs.getString("input_data"),
                            rs.getString("expected_output"),
                            rs.getBoolean("is_sample")
                    ));
                }
            }
        } catch (SQLException e) {
            logger.error("Error fetching test cases for problem {}: {}", problemId, e.getMessage());
        }
        return list;
    }

    public ProblemDTO saveProblem(ProblemDTO problem) throws Exception {
        if (NeonDataSourceConfig.isMockMode()) {
            int id = PROBLEM_ID_GEN.incrementAndGet();
            problem.setId(id);
            MOCK_PROBLEMS.put(id, problem);
            MOCK_PROBLEMS_BY_CODE.put(problem.getCode(), problem);
            return problem;
        }

        String sql = "INSERT INTO problems (code, title, description, time_limit_ms, memory_limit_mb) " +
                     "VALUES (?, ?, ?, ?, ?) RETURNING id";
        try (Connection conn = NeonDataSourceConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, problem.getCode().trim().toUpperCase());
            ps.setString(2, problem.getTitle().trim());
            ps.setString(3, problem.getDescription());
            ps.setInt(4, problem.getTimeLimitMs());
            ps.setInt(5, problem.getMemoryLimitMb());

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    problem.setId(rs.getInt("id"));
                    return problem;
                }
            }
        }
        throw new SQLException("Failed to insert problem");
    }

    public TestCaseDTO addTestCase(TestCaseDTO testCase) throws Exception {
        if (NeonDataSourceConfig.isMockMode()) {
            int id = TEST_CASE_ID_GEN.incrementAndGet();
            testCase.setId(id);
            MOCK_TEST_CASES.computeIfAbsent(testCase.getProblemId(), k -> new ArrayList<>()).add(testCase);
            return testCase;
        }

        String sql = "INSERT INTO test_cases (problem_id, test_order, input_data, expected_output, is_sample) " +
                     "VALUES (?, ?, ?, ?, ?) RETURNING id";
        try (Connection conn = NeonDataSourceConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, testCase.getProblemId());
            ps.setInt(2, testCase.getTestOrder());
            ps.setString(3, testCase.getInputData());
            ps.setString(4, testCase.getExpectedOutput());
            ps.setBoolean(5, testCase.isSample());

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    testCase.setId(rs.getInt("id"));
                    return testCase;
                }
            }
        }
        throw new SQLException("Failed to insert test case");
    }

    private ProblemDTO shallowCopy(ProblemDTO orig) {
        return new ProblemDTO(orig.getId(), orig.getCode(), orig.getTitle(), orig.getDescription(),
                orig.getTimeLimitMs(), orig.getMemoryLimitMb());
    }
}
