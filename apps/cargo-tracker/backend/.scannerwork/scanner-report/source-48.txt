package com.example.cargotracker.booking.application.port;

import java.time.Instant;
import java.util.Map;

/**
 * 複数段にまたがる連鎖の途中経過（ADR-0001 決定 6）。
 *
 * <p>Saga の関連付けにあたるのがこの行そのもので、{@code @EndSaga} にあたるのが
 * {@link Status#COMPLETED} への更新である。完了しても行は消さない。消すと
 * 「いつ終わったか」を後から問えない。</p>
 */
public record ProcessState(
        String processType,
        String processId,
        String currentStep,
        int totalSteps,
        int completedSteps,
        Status status,
        Map<String, String> metadata,
        Instant startedAt,
        Instant updatedAt,
        Instant completedAt) {

    public enum Status {
        RUNNING,
        COMPLETED,
        COMPENSATED
    }

    public ProcessState {
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }

    public boolean isRunning() {
        return status == Status.RUNNING;
    }

    /** 予定の段をすべて終えたか。段が残っているのに完了にすると、抜けに気づけない。 */
    public boolean allStepsDone() {
        return completedSteps >= totalSteps;
    }
}
