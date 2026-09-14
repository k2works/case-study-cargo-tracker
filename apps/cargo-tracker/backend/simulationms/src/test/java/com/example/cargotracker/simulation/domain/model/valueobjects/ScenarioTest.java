package com.example.cargotracker.simulation.domain.model.valueobjects;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.cargotracker.shared.domain.error.BusinessRuleViolation;
import java.util.List;
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
        List<StepKind> steps = Scenario.STANDARD.steps();

        assertThat(steps).startsWith(StepKind.REGISTER_SHIPPER, StepKind.REGISTER_BOOKING);
        assertThat(steps).endsWith(StepKind.RECORD_PAYMENT);
        assertThat(steps)
                .as("**予約から精算までが通ること**が US33 の中核である")
                .contains(StepKind.ASSIGN_ROUTE, StepKind.ISSUE_TRACKING_NUMBER,
                        StepKind.CLEAR_CUSTOMS, StepKind.CLAIM_CARGO,
                        StepKind.CALCULATE_INVOICE);
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
