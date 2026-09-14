package com.example.cargotracker.simulation.domain.model.valueobjects;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.cargotracker.shared.domain.error.BusinessRuleViolation;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * シナリオ（US33 §受入基準 1）。
 *
 * <p><b>工程の並びはシナリオが持つ。</b> 実行が並びを組み立てると、シナリオを
 * 足すたびに実行の側を直すことになる。</p>
 */
class ScenarioTest {

    @Test
    @DisplayName("US33 §1: 標準輸送は予約から精算までを順に含む")
    void standardScenarioRunsFromBookingToSettlement() {
        // **並びをそのまま固定する。** `contains` は順序を見ないので、
        // 引き渡しと経路の確定を入れ替えても緑になる——受入基準 1 が約束して
        // いるのは「順に実行される」ことである（IT16 のレビュー 中）。
        assertThat(Scenario.STANDARD.steps()).containsExactly(
                StepKind.REGISTER_SHIPPER,
                StepKind.REGISTER_BOOKING,
                StepKind.REQUEST_ROUTING,
                StepKind.ASSIGN_ROUTE,
                StepKind.NOTIFY_SHIPPER,
                StepKind.CONFIRM_BOOKING,
                StepKind.ISSUE_TRACKING_NUMBER,
                StepKind.RECORD_HANDLING,
                StepKind.CLEAR_CUSTOMS,
                StepKind.CLAIM_CARGO,
                StepKind.CALCULATE_INVOICE,
                StepKind.ISSUE_INVOICE,
                StepKind.RECORD_PAYMENT);
    }

    @Test
    @DisplayName("工程に重複は無い（同じ工程を二度並べない）")
    void stepsAreNotRepeated() {
        for (Scenario scenario : Scenario.values()) {
            assertThat(scenario.steps())
                    .as("%s の工程", scenario)
                    .doesNotHaveDuplicates();
        }
    }

    @ParameterizedTest
    @EnumSource(Scenario.class)
    @DisplayName("どのシナリオも工程を 1 つ以上持つ（空のシナリオを作らない）")
    void everyScenarioHasSteps(Scenario scenario) {
        // **空のシナリオは「成功」で終わる。** 何も確かめていないのに緑になる。
        assertThat(scenario.steps()).isNotEmpty();
    }

    @Test
    @DisplayName("知らないシナリオは断る（名簿に無いものを通さない）")
    void refusesAnUnknownScenario() {
        assertThatThrownBy(() -> Scenario.of("存在しないシナリオ"))
                .isInstanceOf(BusinessRuleViolation.class)
                .hasMessageContaining("存在しないシナリオ");
    }

    @Test
    @DisplayName("呼び名から引ける（画面は列挙名を出さない）")
    void findsByLabel() {
        assertThat(Scenario.of("一般貨物の標準輸送")).isEqualTo(Scenario.STANDARD);
    }
}
