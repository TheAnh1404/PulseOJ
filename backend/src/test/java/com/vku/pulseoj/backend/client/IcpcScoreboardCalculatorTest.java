package com.vku.pulseoj.backend.client;

import com.vku.pulseoj.common.dto.ContestantDTO;
import com.vku.pulseoj.common.dto.ScoreboardRowDTO;
import com.vku.pulseoj.common.dto.SubmissionDTO;
import com.vku.pulseoj.common.enums.Language;
import com.vku.pulseoj.common.enums.Verdict;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * [TV3 Unit Test] Comprehensive Test Suite for IcpcScoreboardCalculator.
 * Validates international ACM-ICPC rules, 20-minute penalties, edge cases, and tie-breaking.
 */
public class IcpcScoreboardCalculatorTest {

    private long contestStartTime;
    private ContestantDTO alice;
    private ContestantDTO bob;
    private ContestantDTO charlie;

    @BeforeEach
    void setUp() {
        contestStartTime = 1000000000L; // Fixed epoch timestamp
        alice = new ContestantDTO(1, "alice", "Alice Nguyen", false, Instant.now());
        bob = new ContestantDTO(2, "bob", "Bob Tran", false, Instant.now());
        charlie = new ContestantDTO(3, "charlie", "Charlie Le", false, Instant.now());
    }

    private SubmissionDTO createSub(int subId, ContestantDTO contestant, String problemCode,
                                   Verdict verdict, long minutesFromStart) {
        SubmissionDTO sub = new SubmissionDTO(subId, contestant.getId(), contestant.getUsername(),
                100, problemCode, Language.JAVA, "public class Solution {}");
        sub.setVerdict(verdict);
        sub.setSubmittedAt(contestStartTime + (minutesFromStart * 60 * 1000L));
        return sub;
    }

    @Test
    @DisplayName("Edge Case 1: First Attempt AC incurs only solved time and 0 penalty")
    void testFirstAttemptAc_NoPenalty() {
        List<ContestantDTO> contestants = List.of(alice);
        List<SubmissionDTO> subs = List.of(
                createSub(101, alice, "TWO_SUM", Verdict.AC, 15)
        );

        List<ScoreboardRowDTO> board = IcpcScoreboardCalculator.calculate(contestants, subs, contestStartTime);

        assertEquals(1, board.size());
        ScoreboardRowDTO row = board.get(0);
        assertEquals(1, row.getRank());
        assertEquals("alice", row.getUsername());
        assertEquals(1, row.getSolvedCount(), "Should have exactly 1 solved problem");
        assertEquals(15, row.getTotalPenaltyMinutes(), "Penalty should be exactly 15 minutes");

        ScoreboardRowDTO.ProblemScoreDetail detail = row.getProblemDetails().get("TWO_SUM");
        assertNotNull(detail);
        assertTrue(detail.isSolved());
        assertEquals(0, detail.getFailedAttemptsBeforeAc());
        assertEquals(15, detail.getSolvedTimeMinutes());
        assertEquals("+ (15m)", detail.getDisplayCell());
    }

    @Test
    @DisplayName("Edge Case 2: Multiple failed attempts before AC incur 20 minutes penalty each")
    void testMultipleFailedAttempts_ThenAc() {
        List<ContestantDTO> contestants = List.of(bob);
        List<SubmissionDTO> subs = List.of(
                createSub(201, bob, "FACTORIAL", Verdict.WA, 5),
                createSub(202, bob, "FACTORIAL", Verdict.TLE, 15),
                createSub(203, bob, "FACTORIAL", Verdict.RE, 25),
                createSub(204, bob, "FACTORIAL", Verdict.AC, 40)
        );

        List<ScoreboardRowDTO> board = IcpcScoreboardCalculator.calculate(contestants, subs, contestStartTime);

        assertEquals(1, board.size());
        ScoreboardRowDTO row = board.get(0);
        assertEquals(1, row.getSolvedCount());
        // Penalty = 40 + (3 failed attempts * 20) = 40 + 60 = 100
        assertEquals(100, row.getTotalPenaltyMinutes(), "Expected 40m solved + 60m penalty = 100m");

        ScoreboardRowDTO.ProblemScoreDetail detail = row.getProblemDetails().get("FACTORIAL");
        assertNotNull(detail);
        assertTrue(detail.isSolved());
        assertEquals(3, detail.getFailedAttemptsBeforeAc());
        assertEquals("+3 (40m)", detail.getDisplayCell());
    }

    @Test
    @DisplayName("Edge Case 3: Failed attempts on an unsolved problem incur 0 penalty")
    void testMultipleFailedAttempts_NeverAc() {
        List<ContestantDTO> contestants = List.of(charlie);
        List<SubmissionDTO> subs = List.of(
                createSub(301, charlie, "GRAPH", Verdict.WA, 10),
                createSub(302, charlie, "GRAPH", Verdict.TLE, 20),
                createSub(303, charlie, "GRAPH", Verdict.RE, 30)
        );

        List<ScoreboardRowDTO> board = IcpcScoreboardCalculator.calculate(contestants, subs, contestStartTime);

        assertEquals(1, board.size());
        ScoreboardRowDTO row = board.get(0);
        assertEquals(0, row.getSolvedCount(), "Should have 0 solved problems");
        assertEquals(0, row.getTotalPenaltyMinutes(), "Unsolved problem must NOT incur penalty");

        ScoreboardRowDTO.ProblemScoreDetail detail = row.getProblemDetails().get("GRAPH");
        assertNotNull(detail);
        assertFalse(detail.isSolved());
        assertEquals(3, detail.getFailedAttemptsBeforeAc());
        assertEquals("-3", detail.getDisplayCell());
    }

    @Test
    @DisplayName("Edge Case 4: Compilation Error (CE) does not incur 20-minute penalty in ICPC")
    void testCompilationError_NoPenaltyInIcpc() {
        List<ContestantDTO> contestants = List.of(alice);
        List<SubmissionDTO> subs = List.of(
                createSub(401, alice, "BINARY_SEARCH", Verdict.CE, 10), // CE should be ignored for penalty
                createSub(402, alice, "BINARY_SEARCH", Verdict.AC, 25)
        );

        List<ScoreboardRowDTO> board = IcpcScoreboardCalculator.calculate(contestants, subs, contestStartTime);

        assertEquals(1, board.size());
        ScoreboardRowDTO row = board.get(0);
        assertEquals(1, row.getSolvedCount());
        // Penalty should only be 25 minutes (CE does not add 20 minutes)
        assertEquals(25, row.getTotalPenaltyMinutes(), "CE should not add 20-minute penalty");

        ScoreboardRowDTO.ProblemScoreDetail detail = row.getProblemDetails().get("BINARY_SEARCH");
        assertEquals(0, detail.getFailedAttemptsBeforeAc());
        assertEquals("+ (25m)", detail.getDisplayCell());
    }

    @Test
    @DisplayName("Edge Case 5: Submissions submitted after AC are completely ignored")
    void testSubmissionsAfterAc_Ignored() {
        List<ContestantDTO> contestants = List.of(alice);
        List<SubmissionDTO> subs = List.of(
                createSub(501, alice, "DP_KNAPSACK", Verdict.AC, 30),
                createSub(502, alice, "DP_KNAPSACK", Verdict.WA, 45), // Subsequent submission ignored
                createSub(503, alice, "DP_KNAPSACK", Verdict.AC, 55)  // Subsequent submission ignored
        );

        List<ScoreboardRowDTO> board = IcpcScoreboardCalculator.calculate(contestants, subs, contestStartTime);

        assertEquals(1, board.size());
        ScoreboardRowDTO row = board.get(0);
        assertEquals(1, row.getSolvedCount());
        assertEquals(30, row.getTotalPenaltyMinutes(), "Submissions after AC must not alter score or penalty");
    }

    @Test
    @DisplayName("Edge Case 6: Multiple problems and ranking with Tie-breaking")
    void testMultipleProblemsAndRanking_TieBreaking() {
        List<ContestantDTO> contestants = List.of(alice, bob, charlie);
        List<SubmissionDTO> subs = List.of(
                // Alice solves P1 (10m, AC) and P2 (1 fail, 30m, AC) -> 2 solved, penalty = 10 + (30 + 20) = 60m
                createSub(601, alice, "P1", Verdict.AC, 10),
                createSub(602, alice, "P2", Verdict.WA, 15),
                createSub(603, alice, "P2", Verdict.AC, 30),

                // Bob solves P1 (20m, AC) and P2 (0 fail, 50m, AC) -> 2 solved, penalty = 20 + 50 = 70m
                createSub(604, bob, "P1", Verdict.AC, 20),
                createSub(605, bob, "P2", Verdict.AC, 50),

                // Charlie solves P1 (5m, AC) -> 1 solved, penalty = 5m
                createSub(606, charlie, "P1", Verdict.AC, 5)
        );

        List<ScoreboardRowDTO> board = IcpcScoreboardCalculator.calculate(contestants, subs, contestStartTime);

        assertEquals(3, board.size());

        // Rank 1: Alice (2 solved, 60m)
        ScoreboardRowDTO rank1 = board.get(0);
        assertEquals(1, rank1.getRank());
        assertEquals("alice", rank1.getUsername());
        assertEquals(2, rank1.getSolvedCount());
        assertEquals(60, rank1.getTotalPenaltyMinutes());

        // Rank 2: Bob (2 solved, 70m - higher penalty than Alice)
        ScoreboardRowDTO rank2 = board.get(1);
        assertEquals(2, rank2.getRank());
        assertEquals("bob", rank2.getUsername());
        assertEquals(2, rank2.getSolvedCount());
        assertEquals(70, rank2.getTotalPenaltyMinutes());

        // Rank 3: Charlie (1 solved, 5m - fewer solved problems)
        ScoreboardRowDTO rank3 = board.get(2);
        assertEquals(3, rank3.getRank());
        assertEquals("charlie", rank3.getUsername());
        assertEquals(1, rank3.getSolvedCount());
        assertEquals(5, rank3.getTotalPenaltyMinutes());
    }

    @Test
    @DisplayName("Edge Case 7: Identical solved count and penalty result in tied rank")
    void testTiedRankings() {
        List<ContestantDTO> contestants = List.of(alice, bob);
        List<SubmissionDTO> subs = List.of(
                createSub(701, alice, "P1", Verdict.AC, 25),
                createSub(702, bob, "P1", Verdict.AC, 25)
        );

        List<ScoreboardRowDTO> board = IcpcScoreboardCalculator.calculate(contestants, subs, contestStartTime);

        assertEquals(2, board.size());
        assertEquals(1, board.get(0).getRank());
        assertEquals(1, board.get(1).getRank(), "Both contestants must share Rank 1 due to tie");
    }
}

