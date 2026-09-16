package com.example.cargotracker.simulation.domain.model.valueobjects;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * 工程の担当（[ADR-0020] 決定 2）。
 *
 * <p><b>列挙に値を足したら全箇所を回る。</b> 工程を足して担当を決め忘れると、
 * コンパイルが赤になる形（`switch` に `default` を置かない）にしてある。
 * ここでは「全部の工程に担当がいる」ことを値の一覧から回って確かめる。</p>
 */
class StepRoleTest {

    @ParameterizedTest
    @EnumSource(StepKind.class)
    @DisplayName("どの工程にも担当がいる（決め忘れた工程が名乗り出る）")
    void everyStepHasARole(StepKind kind) {
        assertThat(StepRole.of(kind)).isNotNull();
    }

    @Test
    @DisplayName("業務の分担どおりに割り当てる（実装の都合で変えない）")
    void followsTheBusinessDivisionOfWork() {
        // **全部を管理者で叩かない。** 認可を踏まないと、
        // 「シミュレーションは通るのに実際の操作は通らない」状態を検出できない。
        assertThat(StepRole.of(StepKind.REGISTER_BOOKING)).isEqualTo(StepRole.SALES);
        assertThat(StepRole.of(StepKind.ASSIGN_ROUTE)).isEqualTo(StepRole.ROUTING);
        assertThat(StepRole.of(StepKind.RECORD_HANDLING)).isEqualTo(StepRole.HANDLER);
        // 通関の状態更新は追跡管理者（申告は荷役だが、状態は追跡が決める・US29）。
        assertThat(StepRole.of(StepKind.CLEAR_CUSTOMS)).isEqualTo(StepRole.TRACKER);
        assertThat(StepRole.of(StepKind.RECORD_PAYMENT)).isEqualTo(StepRole.ACCOUNTANT);
    }
}
