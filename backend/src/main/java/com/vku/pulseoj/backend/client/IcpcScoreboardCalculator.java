package com.vku.pulseoj.backend.client;

import com.vku.pulseoj.common.dto.ContestantDTO;
import com.vku.pulseoj.common.dto.ScoreboardRowDTO;
import com.vku.pulseoj.common.dto.ScoreboardRowDTO.ProblemScoreDetail;
import com.vku.pulseoj.common.dto.SubmissionDTO;
import com.vku.pulseoj.common.enums.Verdict;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * [TV3] ACM-ICPC Scoreboard Calculator.
 * Calculates solved problems, 20-minute penalty per rejected submission, and rankings.
 */
public class IcpcScoreboardCalculator {

    public static final int PENALTY_MINUTES_PER_REJECTED = 20;

    /**
     * Computes the complete scoreboard given all contestants and submissions.
     *
     * @param contestants List of active contestants
     * @param submissions All submissions sorted chronologically (or will be sorted)
     * @param contestStartTimestamp Epoch milliseconds of contest start
     * @return Sorted list of ScoreboardRowDTO with assigned ranks
     */
    public static List<ScoreboardRowDTO> calculate(List<ContestantDTO> contestants,
                                                  List<SubmissionDTO> submissions,
                                                  long contestStartTimestamp) {
        Map<Integer, ScoreboardRowDTO> rowMap = new HashMap<>();

        for (ContestantDTO c : contestants) {
            ScoreboardRowDTO row = new ScoreboardRowDTO(0, c.getId(), c.getUsername(), c.getFullName(), 0, 0);
            rowMap.put(c.getId(), row);
        }

        // Sort submissions by submitted timestamp
        List<SubmissionDTO> sortedSubmissions = new ArrayList<>(submissions);
        sortedSubmissions.sort(Comparator.comparingLong(SubmissionDTO::getSubmittedAt));

        // Track per-contestant, per-problem state
        // Key: contestantId -> (problemCode -> detail)
        Map<Integer, Map<String, ProblemScoreDetail>> tracker = new HashMap<>();

        for (SubmissionDTO sub : sortedSubmissions) {
            Integer cid = sub.getContestantId();
            if (cid == null || !rowMap.containsKey(cid)) continue;

            String pcode = sub.getProblemCode();
            if (pcode == null || pcode.isBlank()) continue;

            Map<String, ProblemScoreDetail> userProblems = tracker.computeIfAbsent(cid, k -> new HashMap<>());
            ProblemScoreDetail detail = userProblems.computeIfAbsent(pcode, k -> new ProblemScoreDetail(pcode, false, 0, 0, false));

            if (detail.isSolved()) {
                // Once AC, ignore any subsequent submissions on this problem
                continue;
            }

            if (sub.getVerdict() == Verdict.AC) {
                detail.setSolved(true);
                detail.setHasPending(false);
                long elapsedMillis = Math.max(0, sub.getSubmittedAt() - contestStartTimestamp);
                long elapsedMinutes = elapsedMillis / (1000 * 60);
                detail.setSolvedTimeMinutes(elapsedMinutes);
            } else if (sub.getVerdict() == Verdict.PENDING || sub.getVerdict() == Verdict.RUNNING) {
                detail.setHasPending(true);
            } else if (sub.getVerdict() == Verdict.WA || sub.getVerdict() == Verdict.TLE || sub.getVerdict() == Verdict.RE) {
                // Reject incurs 20 min penalty only IF problem is solved later
                detail.setFailedAttemptsBeforeAc(detail.getFailedAttemptsBeforeAc() + 1);
            }
            // Compilation Error (CE) does not add penalty in ICPC
        }

        // Aggregate totals for each contestant
        List<ScoreboardRowDTO> results = new ArrayList<>();
        for (ScoreboardRowDTO row : rowMap.values()) {
            Map<String, ProblemScoreDetail> userProblems = tracker.getOrDefault(row.getContestantId(), Collections.emptyMap());
            row.setProblemDetails(userProblems);

            int solved = 0;
            long penalty = 0;

            for (ProblemScoreDetail detail : userProblems.values()) {
                if (detail.isSolved()) {
                    solved++;
                    penalty += detail.getSolvedTimeMinutes() + ((long) detail.getFailedAttemptsBeforeAc() * PENALTY_MINUTES_PER_REJECTED);
                }
            }

            row.setSolvedCount(solved);
            row.setTotalPenaltyMinutes(penalty);
            results.add(row);
        }

        // Sort rows by ICPC rules: most solved first, then least penalty
        Collections.sort(results);

        // Assign ranks (handles ties)
        int currentRank = 1;
        for (int i = 0; i < results.size(); i++) {
            if (i > 0) {
                ScoreboardRowDTO prev = results.get(i - 1);
                ScoreboardRowDTO curr = results.get(i);
                if (curr.getSolvedCount() == prev.getSolvedCount() &&
                    curr.getTotalPenaltyMinutes() == prev.getTotalPenaltyMinutes()) {
                    curr.setRank(prev.getRank());
                } else {
                    curr.setRank(i + 1);
                }
            } else {
                results.get(0).setRank(1);
            }
        }

        return results;
    }
}
