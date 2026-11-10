package com.vku.pulseoj.common.dto;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * Data Transfer Object representing a competitive programming problem.
 */
public class ProblemDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    private Integer id;
    private String code;
    private String title;
    private String description;
    private int timeLimitMs;
    private int memoryLimitMb;
    private List<TestCaseDTO> testCases = new ArrayList<>();

    public ProblemDTO() {}

    public ProblemDTO(Integer id, String code, String title, String description, int timeLimitMs, int memoryLimitMb) {
        this.id = id;
        this.code = code;
        this.title = title;
        this.description = description;
        this.timeLimitMs = timeLimitMs;
        this.memoryLimitMb = memoryLimitMb;
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public int getTimeLimitMs() {
        return timeLimitMs;
    }

    public void setTimeLimitMs(int timeLimitMs) {
        this.timeLimitMs = timeLimitMs;
    }

    public int getMemoryLimitMb() {
        return memoryLimitMb;
    }

    public void setMemoryLimitMb(int memoryLimitMb) {
        this.memoryLimitMb = memoryLimitMb;
    }

    public List<TestCaseDTO> getTestCases() {
        return testCases;
    }

    public void setTestCases(List<TestCaseDTO> testCases) {
        this.testCases = (testCases != null) ? testCases : new ArrayList<>();
    }

    @Override
    public String toString() {
        return "[" + code + "] " + title;
    }
}
