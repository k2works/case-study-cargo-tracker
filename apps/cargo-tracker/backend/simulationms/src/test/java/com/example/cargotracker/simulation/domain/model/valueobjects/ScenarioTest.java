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

    /**
     * 例外シナリオ（US35 §受入基準 1）。
     *
     * <p><b>4 つは同じ並びで、違うのは種別だけである</b>（注 N10）。
     * <b>値の一覧から回す</b>——1 つずつ書くと、次に足した種類の検査が漏れる。</p>
     */
    @ParameterizedTest
    @EnumSource(value = Scenario.class,
            names = {"DELAY", "DAMAGE", "CUSTOMS_HOLD"})
    @DisplayName("US35 §1・§2: 例外シナリオは起票・対応・解決までを順に含む")
    void exceptionScenariosShareTheSameSteps(Scenario scenario) {
        assertThat(scenario.steps()).containsExactly(
                StepKind.REGISTER_SHIPPER,
                StepKind.REGISTER_BOOKING,
                StepKind.REQUEST_ROUTING,
                StepKind.ASSIGN_ROUTE,
                StepKind.NOTIFY_SHIPPER,
                StepKind.CONFIRM_BOOKING,
                StepKind.ISSUE_TRACKING_NUMBER,
                StepKind.REGISTER_EXCEPTION,
                StepKind.RESPOND_TO_EXCEPTION,
                StepKind.RESOLVE_EXCEPTION);
        assertThat(scenario.exceptionType())
                .as("**種類は入力で変える**（工程は同じ）")
                .isNotNull();
    }

    @Test
    @DisplayName("US35 §3: 誤配は対応の途中で経路を組み直してから解決する")
    void misrouteReassignsTheRouteBeforeResolving() {
        // **解決だけして経路をそのままにすると、同じ港へもう一度運ぶ。**
        assertThat(Scenario.MISROUTE.steps()).containsExactly(
                StepKind.REGISTER_SHIPPER,
                StepKind.REGISTER_BOOKING,
                StepKind.REQUEST_ROUTING,
                StepKind.ASSIGN_ROUTE,
                StepKind.NOTIFY_SHIPPER,
                StepKind.CONFIRM_BOOKING,
                StepKind.ISSUE_TRACKING_NUMBER,
                StepKind.REGISTER_EXCEPTION,
                StepKind.RESPOND_TO_EXCEPTION,
                StepKind.REASSIGN_ROUTE,
                StepKind.RESOLVE_EXCEPTION);
    }

    @Test
    @DisplayName("US35 §4: 輸送中キャンセルは指定港での荷降しまで含む")
    void cancellationRunsThroughTheDischarge() {
        // **承認だけでは追跡は閉じない**（ADR-0018）。貨物はまだ船の上にある。
        assertThat(Scenario.CANCEL_IN_TRANSIT.steps()).containsExactly(
                StepKind.REGISTER_SHIPPER,
                StepKind.REGISTER_BOOKING,
                StepKind.REQUEST_ROUTING,
                StepKind.ASSIGN_ROUTE,
                StepKind.NOTIFY_SHIPPER,
                StepKind.CONFIRM_BOOKING,
                StepKind.ISSUE_TRACKING_NUMBER,
                StepKind.REQUEST_CANCELLATION,
                StepKind.APPROVE_CANCELLATION,
                StepKind.DISCHARGE_CANCELLED);
        assertThat(Scenario.CANCEL_IN_TRANSIT.exceptionType())
                .as("キャンセルは例外ではない（起票しない）")
                .isNull();
    }

    @Test
    @DisplayName("US35 §1: 例外種別は trackingms の列挙にある値だけを使う")
    void everyExceptionTypeIsKnownToTracking() {
        // **境界で翻訳しない。** 種別が増えたときに片方だけが古くなる
        // ——知らない種別で起票すると 400 で止まり、原因が「例外の起票」に
        // 見えてしまう（実際は名前の食い違い）。
        var known = java.util.Set.of("DELAY", "DAMAGE", "LOSS", "MISROUTE", "CUSTOMS_HOLD");
        for (Scenario scenario : Scenario.values()) {
            if (scenario.exceptionType() != null) {
                assertThat(known)
                        .as("%s の例外種別 %s", scenario, scenario.exceptionType())
                        .contains(scenario.exceptionType());
            }
        }
    }

    @Test
    @DisplayName("US35 §1: 例外を起票する工程を持つシナリオは、種別も持つ")
    void scenariosThatReportExceptionsDeclareTheType() {
        // **名簿ではなく数え上げる。** 種別を書き忘れたシナリオは、実行して
        // 初めて「種別がありません」で止まる——宣言の時点で捕まえる。
        for (Scenario scenario : Scenario.values()) {
            if (scenario.steps().contains(StepKind.REGISTER_EXCEPTION)) {
                assertThat(scenario.exceptionType())
                        .as("%s は例外を起票するのに種別が無い", scenario)
                        .isNotNull();
            }
        }
    }

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

    /**
     * 工程に重複は無い。
     *
     * <p><b>同じ工程を二度並べると、2 度目の待ちが空振りする。</b> 待ちは
     * 「その工程の結果が読めるか」で決まるので、1 度目で満たされた条件は
     * 2 度目には最初から満たされている。誤配の経路の組み直し（US35 §3）は
     * 叩く API が同じでも {@code REASSIGN_ROUTE} として分けてある——
     * <b>待つ相手が違うからである</b>。</p>
     */
    @Test
    @DisplayName("工程に重複は無い（2 度目の待ちが空振りする）")
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
