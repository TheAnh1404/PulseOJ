package com.vku.pulseoj.common.dto;

import com.vku.pulseoj.common.enums.Language;
import com.vku.pulseoj.common.enums.Verdict;

import java.io.Serializable;

/**
 * Data Transfer Object representing a code submission.
 */
public class SubmissionDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    private Integer id;
    private Integer contestantId;
    private String contestantUsername;
    private Integer problemId;
    private String problemCode;
    private Language language;
    private String sourceCode;
    private Verdict verdict = Verdict.PENDING;
    private long executionTimeMs;
    private long memoryUsedKb;
    private String compilerOutput;
    private String workerId;
    private long submittedAt;
    private long judgedAt;
    private String clientCallbackToken;

    public SubmissionDTO() {
        this.submittedAt = System.currentTimeMillis();
    }

    public SubmissionDTO(Integer id, Integer contestantId, String contestantUsername, Integer problemId,
                         String problemCode, Language language, String sourceCode) {
        this.id = id;
        this.contestantId = contestantId;
        this.contestantUsername = contestantUsername;
        this.problemId = problemId;
        this.problemCode = problemCode;
        this.language = language;
        this.sourceCode = sourceCode;
        this.verdict = Verdict.PENDING;
        this.submittedAt = System.currentTimeMillis();
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public Integer getContestantId() {
        return contestantId;
    }

    public void setContestantId(Integer contestantId) {
        this.contestantId = contestantId;
    }

    public String getContestantUsername() {
        return contestantUsername;
    }

    public void setContestantUsername(String contestantUsername) {
        this.contestantUsername = contestantUsername;
    }

    public Integer getProblemId() {
        return problemId;
    }

    public void setProblemId(Integer problemId) {
        this.problemId = problemId;
    }

    public String getProblemCode() {
        return problemCode;
    }

    public void setProblemCode(String problemCode) {
        this.problemCode = problemCode;
    }

    public Language getLanguage() {
        return language;
    }

    public void setLanguage(Language language) {
        this.language = language;
    }

    public String getSourceCode() {
        return sourceCode;
    }

    public void setSourceCode(String sourceCode) {
        this.sourceCode = sourceCode;
    }

    public Verdict getVerdict() {
        return verdict;
    }

    public void setVerdict(Verdict verdict) {
        this.verdict = verdict;
    }

    public long getExecutionTimeMs() {
        return executionTimeMs;
    }

    public void setExecutionTimeMs(long executionTimeMs) {
        this.executionTimeMs = executionTimeMs;
    }

    public long getMemoryUsedKb() {
        return memoryUsedKb;
    }

    public void setMemoryUsedKb(long memoryUsedKb) {
        this.memoryUsedKb = memoryUsedKb;
    }

    public String getCompilerOutput() {
        return compilerOutput;
    }

    public void setCompilerOutput(String compilerOutput) {
        this.compilerOutput = compilerOutput;
    }

    public String getWorkerId() {
        return workerId;
    }

    public void setWorkerId(String workerId) {
        this.workerId = workerId;
    }

    public long getSubmittedAt() {
        return submittedAt;
    }

    public void setSubmittedAt(long submittedAt) {
        this.submittedAt = submittedAt;
    }

    public long getJudgedAt() {
        return judgedAt;
    }

    public void setJudgedAt(long judgedAt) {
        this.judgedAt = judgedAt;
    }

    public String getClientCallbackToken() {
        return clientCallbackToken;
    }

    public void setClientCallbackToken(String clientCallbackToken) {
        this.clientCallbackToken = clientCallbackToken;
    }
}
