package com.example.simulationms.domain.model.valueobjects;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/**
 * 1 つの工程の結果（US35）。
 *
 * <p>成否だけでなく<strong>所要時間・生成した識別子・失敗理由</strong>を持つ。
 * 「失敗しました」だけでは、経路候補が 0 件なのか設定が違うのかを切り分けられない。
 */
public record StepResult(ScenarioStep step, StepOutcome outcome, Duration elapsed,
        String createdIdentifier, String failureReason, Instant recordedAt) {

    public StepResult {
        if (step == null || outcome == null || elapsed == null) {
            throw new IllegalArgumentException("工程・結果・所要時間は必須です");
        }
        if (outcome == StepOutcome.FAILED && (failureReason == null || failureReason.isBlank())) {
            throw new IllegalArgumentException("失敗には理由が要ります: " + step);
        }
    }

    public static StepResult succeeded(ScenarioStep step, Duration elapsed, String identifier) {
        return succeeded(step, elapsed, identifier, null);
    }

    public static StepResult succeeded(ScenarioStep step, Duration elapsed, String identifier,
            Instant recordedAt) {
        return new StepResult(step, StepOutcome.SUCCEEDED, elapsed, identifier, null, recordedAt);
    }

    public static StepResult failed(ScenarioStep step, Duration elapsed, String reason) {
        return failed(step, elapsed, reason, null);
    }

    public static StepResult failed(ScenarioStep step, Duration elapsed, String reason,
            Instant recordedAt) {
        return new StepResult(step, StepOutcome.FAILED, elapsed, null, reason, recordedAt);
    }

    public boolean failed() {
        return outcome == StepOutcome.FAILED;
    }

    public Optional<String> identifier() {
        return Optional.ofNullable(createdIdentifier);
    }
}
