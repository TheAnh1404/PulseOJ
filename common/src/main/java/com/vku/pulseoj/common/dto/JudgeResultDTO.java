package com.vku.pulseoj.common.dto;

import com.vku.pulseoj.common.enums.Verdict;

import java.io.Serializable;

/**
 * Real-time incremental feedback emitted by Judge Worker for each test case executed.
 */
public class JudgeResultDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    private Integer submissionId;
    private Verdict verdict;
    private int currentTestIndex;
    private int totalTests;
    private String testInputSnippet;
    private String expectedSnippet;
    private String actualOutputSnippet;
    private long executionTimeMs;
    private long memoryUsedKb;
    private String errorMessage;
    private String workerId;
    private boolean isFinalVerdict;

    public JudgeResultDTO() {}

    public JudgeResultDTO(Integer submissionId, Verdict verdict, int currentTestIndex, int totalTests,
                          long executionTimeMs, long memoryUsedKb, String workerId, boolean isFinalVerdict) {
        this.submissionId = submissionId;
        this.verdict = verdict;
        this.currentTestIndex = currentTestIndex;
        this.totalTests = totalTests;
        this.executionTimeMs = executionTimeMs;
        this.memoryUsedKb = memoryUsedKb;
        this.workerId = workerId;
        this.isFinalVerdict = isFinalVerdict;
    }

    public Integer getSubmissionId() {
        return submissionId;
    }

    public void setSubmissionId(Integer submissionId) {
        this.submissionId = submissionId;
    }

    public Verdict getVerdict() {
        return verdict;
    }

    public void setVerdict(Verdict verdict) {
        this.verdict = verdict;
    }

    public int getCurrentTestIndex() {
        return currentTestIndex;
    }

    public void setCurrentTestIndex(int currentTestIndex) {
        this.currentTestIndex = currentTestIndex;
    }

    public int getTotalTests() {
        return totalTests;
    }

    public void setTotalTests(int totalTests) {
        this.totalTests = totalTests;
    }

    public String getTestInputSnippet() {
        return testInputSnippet;
    }

    public void setTestInputSnippet(String testInputSnippet) {
        this.testInputSnippet = testInputSnippet;
    }

    public String getExpectedSnippet() {
        return expectedSnippet;
    }

    public void setExpectedSnippet(String expectedSnippet) {
        this.expectedSnippet = expectedSnippet;
    }

    public String getActualOutputSnippet() {
        return actualOutputSnippet;
    }

    public void setActualOutputSnippet(String actualOutputSnippet) {
        this.actualOutputSnippet = actualOutputSnippet;
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

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public String getWorkerId() {
        return workerId;
    }

    public void setWorkerId(String workerId) {
        this.workerId = workerId;
    }

    public boolean isFinalVerdict() {
        return isFinalVerdict;
    }

    public void setFinalVerdict(boolean finalVerdict) {
        isFinalVerdict = finalVerdict;
    }

    @Override
    public String toString() {
        return String.format("[Sub #%d] Test %d/%d -> %s (%d ms)", submissionId, currentTestIndex, totalTests, verdict, executionTimeMs);
    }
}
