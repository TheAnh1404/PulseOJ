package com.vku.pulseoj.backend.client;

import com.vku.pulseoj.common.dto.JudgeResultDTO;
import com.vku.pulseoj.common.dto.ScoreboardRowDTO;
import com.vku.pulseoj.common.dto.SubmissionDTO;

import java.util.List;

/**
 * [TV3] Observer interface decoupling Contestant Client backend from Swing UI components.
 */
public interface ClientUiListener {

    void onTestProgress(JudgeResultDTO result);

    void onFinalVerdict(SubmissionDTO submission);

    void onScoreboardUpdated(List<ScoreboardRowDTO> scoreboard);

    void onBroadcastMessage(String message);

    void onConnectionStateChanged(boolean connected, String message);
}
