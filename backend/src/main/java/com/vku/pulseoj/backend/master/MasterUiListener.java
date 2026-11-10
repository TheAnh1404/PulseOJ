package com.vku.pulseoj.backend.master;

import com.vku.pulseoj.common.dto.ContestantDTO;
import com.vku.pulseoj.common.dto.JudgeResultDTO;
import com.vku.pulseoj.common.dto.SubmissionDTO;

/**
 * [TV1] Callback interface decoupling Master Backend logic from Swing UI components.
 * Invoked by Master backend to notify UI of state updates.
 */
public interface MasterUiListener {

    /**
     * Fired when a new submission enters the Master queue.
     */
    void onSubmissionQueued(SubmissionDTO submission);

    /**
     * Fired when a submission task is dispatched to a specific worker.
     */
    void onSubmissionDispatched(int submissionId, String workerId);

    /**
     * Fired when incremental test progress is reported by a worker.
     */
    void onTestProgressUpdate(JudgeResultDTO result);

    /**
     * Fired when a submission is completely judged and final verdict is recorded.
     */
    void onSubmissionCompleted(SubmissionDTO submission);

    /**
     * Fired when a contestant logs in or is modified.
     */
    void onContestantUpdated(ContestantDTO contestant);

    /**
     * Fired when general system status or log message occurs.
     */
    void onServerLog(String message);
}
