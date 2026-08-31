package com.example.simulationms.application.internal.commandservices;

import com.example.simulationms.domain.model.valueobjects.RunId;

/**
 * 同じシナリオが既に実行中である（US34-5）。
 *
 * <p><strong>実行中の ID を持たせる。</strong>「実行中です」だけで断ると、
 * 指示した人はいま何が動いているかを確かめる手段が無い——気づく手段は次の行動へ繋ぐ。
 */
public class SimulationAlreadyRunningException extends RuntimeException {

    private final transient RunId runningRunId;

    public SimulationAlreadyRunningException(RunId runningRunId) {
        super("同じシナリオが実行中です: " + runningRunId.value());
        this.runningRunId = runningRunId;
    }

    public RunId runningRunId() {
        return runningRunId;
    }
}
