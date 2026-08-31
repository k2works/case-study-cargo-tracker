package com.example.simulationms.domain.model.valueobjects;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * 例外を含むシナリオ（US36・[ADR-031] 決定 5）。
 *
 * <p><strong>例外専用の入口を作らない。</strong>誤配は「予定と違う港での荷役を記録する」、
 * 遅延は「予定より遅い日時で記録する」——実際に起きる操作をそのまま並べる。
 * 専用 API を作ると、[ADR-026](誤配は荷役の記録から検知する) の検知を通らない経路を
 * 新設することになり、<strong>実際には動かない実装が緑になる</strong>。
 *
 * <p>ここで確かめるのは<strong>並びが業務として成り立っているか</strong>である。
 * 例外は起こしただけでは仕事にならない——対応（解決・組み直し・承認）まで並んで
 * はじめて「例外が起きたあとの業務」を通したことになる。
 */
@DisplayName("例外を含むシナリオ")
class ExceptionScenarioTest {

    /** 例外シナリオは、正常系と同じく追跡番号の発行までを土台にする。 */
    private static final List<ScenarioStep> UNTIL_TRACKING = List.of(
            ScenarioStep.REGISTER_SHIPPER, ScenarioStep.REGISTER_BOOKING,
            ScenarioStep.REQUEST_ROUTING, ScenarioStep.REGISTER_VOYAGE,
            ScenarioStep.ASSIGN_ROUTE, ScenarioStep.NOTIFY_ROUTE,
            ScenarioStep.CONFIRM_BOOKING, ScenarioStep.ISSUE_TRACKING_NUMBER);

    @Nested
    @DisplayName("並び")
    class Steps {

        @Test
        @DisplayName("5 つの例外シナリオが選べる")
        void offersEveryExceptionScenario() {
            assertThat(Scenario.exceptionScenarios())
                    .extracting(Scenario::id)
                    .containsExactlyInAnyOrder("delay", "damage", "misroute",
                            "customs-hold", "cancellation");
        }

        @Test
        @DisplayName("どの例外シナリオも、追跡番号の発行までは正常系と同じ道を通る")
        void everyExceptionScenarioSharesTheHappyPathUntilTracking() {
            for (Scenario scenario : Scenario.exceptionScenarios()) {
                assertThat(scenario.steps().subList(0, UNTIL_TRACKING.size()))
                        .as("%s の土台", scenario.id())
                        .isEqualTo(UNTIL_TRACKING);
            }
        }

        /**
         * <strong>起こしただけでは仕事にならない。</strong>US36-2 が求めているのは
         * 「対応まで含めて実行される」ことである。
         */
        @Test
        @DisplayName("例外を起こす工程には、対応する工程が続く")
        void everyExceptionIsFollowedByItsResponse() {
            for (Scenario scenario : Scenario.exceptionScenarios()) {
                assertThat(scenario.steps())
                        .as("%s に例外を起こす工程がある", scenario.id())
                        .anyMatch(ScenarioStep::raisesException);
                assertThat(scenario.steps())
                        .as("%s に対応の工程がある", scenario.id())
                        .anyMatch(ScenarioStep::respondsToException);
                assertThat(indexOfFirst(scenario, ScenarioStep::raisesException))
                        .as("%s では、対応が例外より後にある", scenario.id())
                        .isLessThan(indexOfFirst(scenario, ScenarioStep::respondsToException));
            }
        }

        /**
         * 誤配の対応は<strong>現在地からの組み直し</strong>である（US36-3）。
         * 元の経路を割り当て直すのでは、輸送は再開しない。
         */
        @Test
        @DisplayName("誤配は、現在地からの経路の組み直しで復帰する")
        void misrouteIsResolvedByRedesigningFromCurrentLocation() {
            Scenario misroute = scenario("misroute");

            assertThat(misroute.steps())
                    .containsSubsequence(ScenarioStep.RECORD_MISROUTED_HANDLING,
                            ScenarioStep.REDESIGN_ROUTE);
        }

        /** キャンセルは追跡管理者の承認まで通す（US36-2）。申請だけでは状態は変わらない。 */
        @Test
        @DisplayName("輸送中キャンセルは、承認まで通す")
        void cancellationRunsThroughApproval() {
            Scenario cancellation = scenario("cancellation");

            assertThat(cancellation.steps())
                    .containsSubsequence(ScenarioStep.REQUEST_CANCELLATION,
                            ScenarioStep.APPROVE_CANCELLATION);
        }

        /**
         * <strong>キャンセルした貨物は精算まで進めない。</strong>並べると必ず失敗し、
         * 「シナリオが落ちた」と読めてしまう——落ちたのは業務ではなく並べ方である。
         */
        @Test
        @DisplayName("キャンセルのシナリオは、引取と精算を並べない")
        void cancellationDoesNotContinueToSettlement() {
            assertThat(scenario("cancellation").steps())
                    .doesNotContain(ScenarioStep.RECORD_CLAIM, ScenarioStep.SETTLE);
        }

        /** 遅延・破損・誤配・税関保留は、対応したうえで**精算まで通る**（US36-2）。 */
        @Test
        @DisplayName("キャンセル以外は、対応したあと精算まで通る")
        void otherScenariosStillReachSettlement() {
            for (Scenario scenario : Scenario.exceptionScenarios()) {
                if ("cancellation".equals(scenario.id())) {
                    continue;
                }
                assertThat(scenario.steps())
                        .as("%s は精算まで通る", scenario.id())
                        .contains(ScenarioStep.SETTLE);
            }
        }
    }

    @Nested
    @DisplayName("踏む人")
    class Roles {

        /**
         * <strong>工程を足したら、踏む人も決まっていなければならない。</strong>
         * 空のまま通すと、名簿に無いロールとして実行時に初めて落ちる。
         */
        @Test
        @DisplayName("足した工程にも、踏むロールがある")
        void everyStepHasARole() {
            assertThat(ScenarioStep.values())
                    .allSatisfy(step -> assertThat(step.role())
                            .as("%s を踏むロール", step)
                            .isNotBlank());
        }

        /** 例外の解決とキャンセルの承認は追跡管理者の業務である（[ADR-024]・US30）。 */
        @Test
        @DisplayName("例外の解決とキャンセルの承認は、追跡管理者が踏む")
        void trackerRespondsToExceptions() {
            assertThat(ScenarioStep.RESOLVE_EXCEPTION.role()).isEqualTo("ROLE_TRACKER");
            assertThat(ScenarioStep.APPROVE_CANCELLATION.role()).isEqualTo("ROLE_TRACKER");
        }

        /** 経路の組み直しは経路設計者の業務である（US28）。 */
        @Test
        @DisplayName("経路の組み直しは、経路設計者が踏む")
        void routingRedesignsTheRoute() {
            assertThat(ScenarioStep.REDESIGN_ROUTE.role()).isEqualTo("ROLE_ROUTING");
        }
    }

    private static Scenario scenario(String id) {
        return Scenario.exceptionScenarios().stream()
                .filter(candidate -> candidate.id().equals(id))
                .findFirst()
                .orElseThrow(() -> new AssertionError("シナリオがありません: " + id));
    }

    private static int indexOfFirst(Scenario scenario,
            java.util.function.Predicate<ScenarioStep> predicate) {
        List<ScenarioStep> steps = scenario.steps();
        for (int i = 0; i < steps.size(); i++) {
            if (predicate.test(steps.get(i))) {
                return i;
            }
        }
        throw new AssertionError("該当する工程がありません: " + scenario.id());
    }
}
