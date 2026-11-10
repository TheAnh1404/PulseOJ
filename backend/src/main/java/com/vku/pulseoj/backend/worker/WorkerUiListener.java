package com.vku.pulseoj.backend.worker;

import com.vku.pulseoj.common.dto.JudgeResultDTO;
import com.vku.pulseoj.common.dto.SubmissionDTO;
import com.vku.pulseoj.common.enums.WorkerStatus;

/**
 * [TV2] Callback interface decoupling Judge Worker backend from Swing UI components.
 */
public interface WorkerUiListener {

    void onWorkerStatusChanged(WorkerStatus status);

    void onJobReceived(SubmissionDTO submission);

    void onTestExecuting(int currentTest, int totalTests);

    void onTestFinished(JudgeResultDTO result);

    void onJobFinished(SubmissionDTO submission);

    void onSandboxConsoleOutput(String line);
}
