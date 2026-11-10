package com.vku.pulseoj.backend.db;

import com.vku.pulseoj.common.dto.SubmissionDTO;
import com.vku.pulseoj.common.enums.Language;
import com.vku.pulseoj.common.enums.Verdict;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * [TV1] Data Access Object for Submissions.
 * Persists submissions and test outcomes into Neon PostgreSQL.
 */
public class SubmissionDAO {

    private static final Logger logger = LoggerFactory.getLogger(SubmissionDAO.class);

    private static final Map<Integer, SubmissionDTO> MOCK_SUBMISSIONS = new ConcurrentHashMap<>();
    private static final AtomicInteger SUBMISSION_ID_GEN = new AtomicInteger(1000);

    /**
     * Persists a new submission in PENDING state.
     */
    public SubmissionDTO saveSubmission(SubmissionDTO submission) throws Exception {
        if (NeonDataSourceConfig.isMockMode()) {
            int id = SUBMISSION_ID_GEN.incrementAndGet();
            submission.setId(id);
            submission.setVerdict(Verdict.PENDING);
            submission.setSubmittedAt(System.currentTimeMillis());
            MOCK_SUBMISSIONS.put(id, submission);
            return submission;
        }

        String sql = "INSERT INTO submissions (contestant_id, problem_id, language, source_code, verdict, submitted_at) " +
                     "VALUES (?, ?, ?, ?, ?, ?) RETURNING id";
        try (Connection conn = NeonDataSourceConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, submission.getContestantId());
            ps.setInt(2, submission.getProblemId());
            ps.setString(3, submission.getLanguage().name());
            ps.setString(4, submission.getSourceCode());
            ps.setString(5, Verdict.PENDING.name());
            ps.setTimestamp(6, new Timestamp(submission.getSubmittedAt()));

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    submission.setId(rs.getInt("id"));
                    submission.setVerdict(Verdict.PENDING);
                    return submission;
                }
            }
        }
        throw new SQLException("Failed to insert submission");
    }

    /**
     * Updates submission verdict and telemetry upon judging completion.
     */
    public void updateVerdict(int submissionId, Verdict verdict, long executionTimeMs,
                              long memoryUsedKb, String compilerOutput, String workerId) {
        if (NeonDataSourceConfig.isMockMode()) {
            SubmissionDTO sub = MOCK_SUBMISSIONS.get(submissionId);
            if (sub != null) {
                sub.setVerdict(verdict);
                sub.setExecutionTimeMs(executionTimeMs);
                sub.setMemoryUsedKb(memoryUsedKb);
                sub.setCompilerOutput(compilerOutput);
                sub.setWorkerId(workerId);
                sub.setJudgedAt(System.currentTimeMillis());
            }
            return;
        }

        String sql = "UPDATE submissions SET verdict = ?, execution_time_ms = ?, memory_used_kb = ?, " +
                     "compiler_output = ?, worker_id = ?, judged_at = ? WHERE id = ?";
        try (Connection conn = NeonDataSourceConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, verdict.name());
            ps.setLong(2, executionTimeMs);
            ps.setLong(3, memoryUsedKb);
            ps.setString(4, compilerOutput);
            ps.setString(5, workerId);
            ps.setTimestamp(6, new Timestamp(System.currentTimeMillis()));
            ps.setInt(7, submissionId);
            ps.executeUpdate();
        } catch (SQLException e) {
            logger.error("Error updating verdict for submission {}: {}", submissionId, e.getMessage());
        }
    }

    public SubmissionDTO getById(int submissionId) {
        if (NeonDataSourceConfig.isMockMode()) {
            return MOCK_SUBMISSIONS.get(submissionId);
        }

        String sql = "SELECT s.*, c.username AS contestant_username, p.code AS problem_code " +
                     "FROM submissions s " +
                     "JOIN contestants c ON s.contestant_id = c.id " +
                     "JOIN problems p ON s.problem_id = p.id " +
                     "WHERE s.id = ?";
        try (Connection conn = NeonDataSourceConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, submissionId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapRow(rs);
                }
            }
        } catch (SQLException e) {
            logger.error("Error retrieving submission {}: {}", submissionId, e.getMessage());
        }
        return null;
    }

    public List<SubmissionDTO> getByContestant(int contestantId) {
        List<SubmissionDTO> list = new ArrayList<>();
        if (NeonDataSourceConfig.isMockMode()) {
            for (SubmissionDTO sub : MOCK_SUBMISSIONS.values()) {
                if (sub.getContestantId() != null && sub.getContestantId() == contestantId) {
                    list.add(sub);
                }
            }
            return list;
        }

        String sql = "SELECT s.*, c.username AS contestant_username, p.code AS problem_code " +
                     "FROM submissions s " +
                     "JOIN contestants c ON s.contestant_id = c.id " +
                     "JOIN problems p ON s.problem_id = p.id " +
                     "WHERE s.contestant_id = ? ORDER BY s.id DESC";
        try (Connection conn = NeonDataSourceConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, contestantId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapRow(rs));
                }
            }
        } catch (SQLException e) {
            logger.error("Error retrieving submissions for contestant {}: {}", contestantId, e.getMessage());
        }
        return list;
    }

    public List<SubmissionDTO> getAllSubmissions() {
        List<SubmissionDTO> list = new ArrayList<>();
        if (NeonDataSourceConfig.isMockMode()) {
            list.addAll(MOCK_SUBMISSIONS.values());
            return list;
        }

        String sql = "SELECT s.*, c.username AS contestant_username, p.code AS problem_code " +
                     "FROM submissions s " +
                     "JOIN contestants c ON s.contestant_id = c.id " +
                     "JOIN problems p ON s.problem_id = p.id " +
                     "ORDER BY s.id DESC";
        try (Connection conn = NeonDataSourceConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                list.add(mapRow(rs));
            }
        } catch (SQLException e) {
            logger.error("Error retrieving all submissions: {}", e.getMessage());
        }
        return list;
    }

    private SubmissionDTO mapRow(ResultSet rs) throws SQLException {
        SubmissionDTO sub = new SubmissionDTO();
        sub.setId(rs.getInt("id"));
        sub.setContestantId(rs.getInt("contestant_id"));
        sub.setContestantUsername(rs.getString("contestant_username"));
        sub.setProblemId(rs.getInt("problem_id"));
        sub.setProblemCode(rs.getString("problem_code"));
        sub.setLanguage(Language.valueOf(rs.getString("language")));
        sub.setSourceCode(rs.getString("source_code"));
        sub.setVerdict(Verdict.valueOf(rs.getString("verdict")));
        sub.setExecutionTimeMs(rs.getLong("execution_time_ms"));
        sub.setMemoryUsedKb(rs.getLong("memory_used_kb"));
        sub.setCompilerOutput(rs.getString("compiler_output"));
        sub.setWorkerId(rs.getString("worker_id"));

        Timestamp subTs = rs.getTimestamp("submitted_at");
        if (subTs != null) sub.setSubmittedAt(subTs.getTime());

        Timestamp judgeTs = rs.getTimestamp("judged_at");
        if (judgeTs != null) sub.setJudgedAt(judgeTs.getTime());

        return sub;
    }
}
