package com.example.simulationms.domain.model.valueobjects;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("シナリオと工程")
class ScenarioTest {

    @Test
    @DisplayName("標準輸送は、荷主登録から精算までを順に踏む")
    void standardTransportCoversEveryStep() {
        Scenario scenario = Scenario.standardTransport();

        assertThat(scenario.steps()).containsExactly(
                ScenarioStep.REGISTER_SHIPPER, ScenarioStep.REGISTER_BOOKING,
                ScenarioStep.REQUEST_ROUTING, ScenarioStep.REGISTER_VOYAGE,
                ScenarioStep.ASSIGN_ROUTE, ScenarioStep.NOTIFY_ROUTE,
                ScenarioStep.CONFIRM_BOOKING, ScenarioStep.ISSUE_TRACKING_NUMBER,
                ScenarioStep.RECORD_HANDLING, ScenarioStep.DECLARE_CUSTOMS,
                ScenarioStep.CLEAR_CUSTOMS, ScenarioStep.RECORD_CLAIM,
                ScenarioStep.CALCULATE_CHARGE, ScenarioStep.SETTLE);
        assertThat(scenario.steps().getFirst()).isEqualTo(ScenarioStep.REGISTER_SHIPPER);
        assertThat(scenario.last()).isEqualTo(ScenarioStep.SETTLE);
        assertThat(scenario.includes(ScenarioStep.CLEAR_CUSTOMS)).isTrue();
    }

    /**
     * <strong>正常系に例外の工程を混ぜない</strong>（US36 で工程を足したときの検査）。
     *
     * <p>IT14 は {@code values()} をそのまま並べていた。その形のままだと、
     * 例外の工程を足した瞬間に<strong>正常系のシナリオが誤配や破損を起こす</strong>。
     */
    @Test
    @DisplayName("標準輸送には、例外を起こす工程も対応する工程も入らない")
    void standardTransportHasNoExceptionSteps() {
        assertThat(Scenario.standardTransport().steps())
                .noneMatch(ScenarioStep::raisesException)
                .noneMatch(ScenarioStep::respondsToException);
    }

    @Test
    @DisplayName("工程の無いシナリオは作れない")
    void rejectsEmptySteps() {
        List<ScenarioStep> none = List.of();

        assertThatThrownBy(() -> Scenario.of("empty", none))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("同じ工程を 2 度並べたシナリオは作れない")
    void rejectsDuplicatedSteps() {
        List<ScenarioStep> twice = List.of(ScenarioStep.SETTLE, ScenarioStep.SETTLE);

        assertThatThrownBy(() -> Scenario.of("dup", twice))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("2 度");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " "})
    @DisplayName("名前の無いシナリオは作れない")
    void rejectsBlankId(String id) {
        List<ScenarioStep> steps = List.of(ScenarioStep.SETTLE);

        assertThatThrownBy(() -> Scenario.of(id, steps))
                .isInstanceOf(IllegalArgumentException.class);
    }

    /**
     * <strong>値の一覧から回す。</strong>工程を足したときに、ロールや表示名を
     * 埋め忘れた値は名乗り出ない（列挙に値を足したら全箇所を回る）。
     */
    @ParameterizedTest
    @EnumSource(ScenarioStep.class)
    @DisplayName("すべての工程が、表示名と踏む人のロールを持つ")
    void everyStepHasLabelAndRole(ScenarioStep step) {
        assertThat(step.label()).isNotBlank();
        assertThat(step.role()).startsWith("ROLE_");
    }

    @ParameterizedTest
    @EnumSource(RunStatus.class)
    @DisplayName("すべての実行状態が表示名を持つ")
    void everyRunStatusHasLabel(RunStatus status) {
        assertThat(status.label()).isNotBlank();
    }

    @ParameterizedTest
    @EnumSource(StepOutcome.class)
    @DisplayName("すべての工程の結果が表示名を持つ")
    void everyOutcomeHasLabel(StepOutcome outcome) {
        assertThat(outcome.label()).isNotBlank();
    }

    @ParameterizedTest
    @ValueSource(strings = {"SIM-2026-0001", "sim-20261116-0001", "SIM-20261116-001", "SIM-20261116"})
    @DisplayName("形の違う実行 ID は受け付けない")
    void rejectsMalformedRunId(String value) {
        assertThatThrownBy(() -> RunId.of(value)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("失敗した工程には理由が要る")
    void requiresFailureReason() {
        Duration elapsed = Duration.ofMillis(1);

        assertThatThrownBy(() -> new StepResult(ScenarioStep.SETTLE, StepOutcome.FAILED,
                elapsed, null, " ", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("理由");
    }

    @Test
    @DisplayName("工程・結果・所要時間の無い記録は作れない")
    void requiresStepOutcomeAndElapsed() {
        Duration elapsed = Duration.ofMillis(1);

        assertThatThrownBy(() -> StepResult.succeeded(null, elapsed, "X-1"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> StepResult.succeeded(ScenarioStep.SETTLE, null, "X-1"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("識別子を残さなかった工程は、識別子を持たない")
    void hasNoIdentifierWhenNotCreated() {
        assertThat(StepResult.failed(ScenarioStep.SETTLE, Duration.ofMillis(1), "だめ")
                .identifier()).isEmpty();
    }
}
