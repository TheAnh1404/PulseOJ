package com.vku.pulseoj.common.dto;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

/**
 * ACM-ICPC Scoreboard entry representing contestant rank, solved count, and penalty minutes.
 */
public class ScoreboardRowDTO implements Serializable, Comparable<ScoreboardRowDTO> {
    private static final long serialVersionUID = 1L;

    private int rank;
    private Integer contestantId;
    private String username;
    private String fullName;
    private int solvedCount;
    private long totalPenaltyMinutes;
    private Map<String, ProblemScoreDetail> problemDetails = new HashMap<>();

    public ScoreboardRowDTO() {}

    public ScoreboardRowDTO(int rank, Integer contestantId, String username, String fullName, int solvedCount, long totalPenaltyMinutes) {
        this.rank = rank;
        this.contestantId = contestantId;
        this.username = username;
        this.fullName = fullName;
        this.solvedCount = solvedCount;
        this.totalPenaltyMinutes = totalPenaltyMinutes;
    }

    public int getRank() {
        return rank;
    }

    public void setRank(int rank) {
        this.rank = rank;
    }

    public Integer getContestantId() {
        return contestantId;
    }

    public void setContestantId(Integer contestantId) {
        this.contestantId = contestantId;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public int getSolvedCount() {
        return solvedCount;
    }

    public void setSolvedCount(int solvedCount) {
        this.solvedCount = solvedCount;
    }

    public long getTotalPenaltyMinutes() {
        return totalPenaltyMinutes;
    }

    public void setTotalPenaltyMinutes(long totalPenaltyMinutes) {
        this.totalPenaltyMinutes = totalPenaltyMinutes;
    }

    public Map<String, ProblemScoreDetail> getProblemDetails() {
        return problemDetails;
    }

    public void setProblemDetails(Map<String, ProblemScoreDetail> problemDetails) {
        this.problemDetails = (problemDetails != null) ? problemDetails : new HashMap<>();
    }

    @Override
    public int compareTo(ScoreboardRowDTO other) {
        // ICPC Rule: Higher solvedCount comes first, then lower penaltyMinutes
        if (this.solvedCount != other.solvedCount) {
            return Integer.compare(other.solvedCount, this.solvedCount);
        }
        return Long.compare(this.totalPenaltyMinutes, other.totalPenaltyMinutes);
    }

    public static class ProblemScoreDetail implements Serializable {
        private static final long serialVersionUID = 1L;

        private String problemCode;
        private boolean solved;
        private int failedAttemptsBeforeAc;
        private long solvedTimeMinutes;
        private boolean hasPending;

        public ProblemScoreDetail() {}

        public ProblemScoreDetail(String problemCode, boolean solved, int failedAttemptsBeforeAc, long solvedTimeMinutes, boolean hasPending) {
            this.problemCode = problemCode;
            this.solved = solved;
            this.failedAttemptsBeforeAc = failedAttemptsBeforeAc;
            this.solvedTimeMinutes = solvedTimeMinutes;
            this.hasPending = hasPending;
        }

        public String getProblemCode() {
            return problemCode;
        }

        public void setProblemCode(String problemCode) {
            this.problemCode = problemCode;
        }

        public boolean isSolved() {
            return solved;
        }

        public void setSolved(boolean solved) {
            this.solved = solved;
        }

        public int getFailedAttemptsBeforeAc() {
            return failedAttemptsBeforeAc;
        }

        public void setFailedAttemptsBeforeAc(int failedAttemptsBeforeAc) {
            this.failedAttemptsBeforeAc = failedAttemptsBeforeAc;
        }

        public long getSolvedTimeMinutes() {
            return solvedTimeMinutes;
        }

        public void setSolvedTimeMinutes(long solvedTimeMinutes) {
            this.solvedTimeMinutes = solvedTimeMinutes;
        }

        public boolean isHasPending() {
            return hasPending;
        }

        public void setHasPending(boolean hasPending) {
            this.hasPending = hasPending;
        }

        public String getDisplayCell() {
            if (solved) {
                return "+" + (failedAttemptsBeforeAc > 0 ? failedAttemptsBeforeAc : "") + " (" + solvedTimeMinutes + "m)";
            } else if (hasPending) {
                return "? (" + failedAttemptsBeforeAc + ")";
            } else if (failedAttemptsBeforeAc > 0) {
                return "-" + failedAttemptsBeforeAc;
            } else {
                return ".";
            }
        }
    }
}
