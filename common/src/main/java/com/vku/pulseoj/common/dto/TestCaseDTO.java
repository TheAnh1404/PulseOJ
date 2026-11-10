package com.vku.pulseoj.common.dto;

import java.io.Serializable;

/**
 * Data Transfer Object representing a single problem test case.
 */
public class TestCaseDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    private Integer id;
    private Integer problemId;
    private int testOrder;
    private String inputData;
    private String expectedOutput;
    private boolean sample;

    public TestCaseDTO() {}

    public TestCaseDTO(Integer id, Integer problemId, int testOrder, String inputData, String expectedOutput, boolean sample) {
        this.id = id;
        this.problemId = problemId;
        this.testOrder = testOrder;
        this.inputData = inputData;
        this.expectedOutput = expectedOutput;
        this.sample = sample;
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public Integer getProblemId() {
        return problemId;
    }

    public void setProblemId(Integer problemId) {
        this.problemId = problemId;
    }

    public int getTestOrder() {
        return testOrder;
    }

    public void setTestOrder(int testOrder) {
        this.testOrder = testOrder;
    }

    public String getInputData() {
        return inputData;
    }

    public void setInputData(String inputData) {
        this.inputData = inputData;
    }

    public String getExpectedOutput() {
        return expectedOutput;
    }

    public void setExpectedOutput(String expectedOutput) {
        this.expectedOutput = expectedOutput;
    }

    public boolean isSample() {
        return sample;
    }

    public void setSample(boolean sample) {
        this.sample = sample;
    }
}
