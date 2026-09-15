package com.example.cargotracker.simulation.domain.model.services;

import static org.assertj.core.api.Assertions.assertThat;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.cargotracker.shared.domain.error.BusinessRuleViolation;
import com.example.cargotracker.simulation.domain.model.valueobjects.ScenarioInput;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 乱数でシナリオと条件を選ぶ（US36 §受入基準 1・3）。
 *
 * <p><b>同じ種なら同じ並び。</b> 切り分けは「同じことをもう一度起こす」ことから
 * 始まる。再現できないと、たまたま出た失敗を追えない。</p>
 *
 * <p><b>再現するのは選んだ条件だけ</b>（注 N4）。実行の時刻と生成される識別子は
 * 種の外である（UUID と採番は乱数が決めない）。この線引きを書かないと
 * 「再現できない」と読まれる。</p>
 */
class RandomScenarioTest {

    private static List<ScenarioInput> take(long seed, int count) {
        RandomScenario random = RandomScenario.from(seed, 0.5);
        return IntStream.range(0, count).mapToObj(index -> random.next()).toList();
    }

    @Test
    @DisplayName("US36 §3: 同じ種を指定すると、同じ並びを再現できる")
    void reproducesTheSameSequenceForTheSameSeed() {
        assertThat(take(42L, 20))
                .as("**20 件そろって一致する。** 1 件だけ見ると、たまたま同じ値でも通る")
                .containsExactlyElementsOf(take(42L, 20));
    }

    @Test
    @DisplayName("US36 §3: 違う種なら違う並びになる（検査が空振りしていない）")
    void differsForADifferentSeed() {
        assertThat(take(42L, 20))
                .as("**すべて同じ値を返す実装でも上の検査は緑になる。**")
                .isNotEqualTo(take(43L, 20));
    }

    @Test
    @DisplayName("US36 §1: 選ぶのはシナリオ・出発地・目的地・貨物種別・重量・期限")
    void choosesEveryFieldOfTheInput() {
        List<ScenarioInput> inputs = take(7L, 50);

        assertThat(inputs).allSatisfy(input -> {
            assertThat(input.scenario()).isNotNull();
            assertThat(input.originUnLocode()).isNotBlank();
            assertThat(input.destinationUnLocode()).isNotBlank();
            assertThat(input.cargoType()).isNotBlank();
            assertThat(input.weightKg()).isPositive();
            assertThat(input.arrivalDeadlineDays()).isPositive();
        });
    }

    @Test
    @DisplayName("出発地と目的地は同じにならない（業務が必ず断る組を作らない）")
    void neverPicksTheSamePortTwice() {
        // 断られる組ばかり作ると、確かめたい経路を通らないまま「失敗」が積み上がる。
        assertThat(take(3L, 200))
                .allSatisfy(input -> assertThat(input.originUnLocode())
                        .isNotEqualTo(input.destinationUnLocode()));
    }

    @Test
    @DisplayName("条件そのものが同じ港の組を断る（作る側だけの守りにしない）")
    void theInputItselfRefusesTheSamePortTwice() {
        // **守りを 1 か所に置く。** 乱数の側だけで避けると、条件を手で組む
        // 経路（US35 の例外シナリオ）が同じ間違いを通す。
        assertThatThrownBy(() -> new ScenarioInput(
                com.example.cargotracker.simulation.domain.model.valueobjects.Scenario.STANDARD,
                "JPTYO", "JPTYO", "GENERAL", java.math.BigDecimal.TEN, 30))
                .isInstanceOf(BusinessRuleViolation.class);
        assertThatThrownBy(() -> new ScenarioInput(
                com.example.cargotracker.simulation.domain.model.valueobjects.Scenario.STANDARD,
                null, "JPTYO", "GENERAL", java.math.BigDecimal.TEN, 30))
                .isInstanceOf(BusinessRuleViolation.class);
    }

    @Test
    @DisplayName("US36 §1: 選ばれる値は一覧から回して数え上げる（偏りに気づける）")
    void eventuallyChoosesEveryScenario() {
        // **名簿に載せたものが実際に出るか**を確かめる。足しただけで出ない値は、
        // 一覧に並んでいても誰も踏まない。
        assertThat(take(11L, 500))
                .extracting(ScenarioInput::scenario)
                .containsAll(List.of(
                        com.example.cargotracker.simulation.domain.model.valueobjects
                                .Scenario.values()));
    }

    @Test
    @DisplayName("US36 §1・§3: すでに流した本数だけ進めた種は、通しで引いた並びと一致する")
    void resumesWhereTheSequenceLeftOff() {
        // **稼働は記憶を持たない**（毎回 DB から組み直す）ので、位置を記憶に
        // 頼ると組み直すたびに先頭へ戻り、**同じ条件を延々と流す**
        // （IT17 のレビューで実測。US36 §1 が成立していなかった）。
        List<ScenarioInput> straight = take(42L, 5);

        for (int drawn = 0; drawn < 5; drawn++) {
            assertThat(RandomScenario.from(42L, 0.5, drawn).next())
                    .as("%d 本流したあとの %d 本目", drawn, drawn + 1)
                    .isEqualTo(straight.get(drawn));
        }
    }

    @Test
    @DisplayName("US36 §1: 続けて引くと条件が変わる（同じものを延々と流さない）")
    void drawsDifferentInputsAsItGoes() {
        assertThat(take(42L, 10))
                .as("**同じ条件ばかり流すと、確かめている経路が 1 本に縮む**")
                .doesNotHaveDuplicates();
    }

    @Test
    @DisplayName("US36 §2: 例外の発生比率が効く（宣言だけの設定にしない）")
    void honoursTheExceptionRatio() {
        // **検証して保存して画面に出しても、選び方に効いていなければ
        // 何も変わらない**——定義済み未使用は配線漏れのサインである。
        assertThat(exceptionRateOf(0.0))
                .as("0 なら例外シナリオを引かない")
                .isZero();
        assertThat(exceptionRateOf(1.0))
                .as("1 なら例外シナリオだけを引く")
                .isEqualTo(1.0);
        assertThat(exceptionRateOf(0.2))
                .as("**間の値も効く。** 0 と 1 だけだと、真偽値の実装でも緑になる")
                .isBetween(0.10, 0.30);
    }

    /** 200 件引いたときの、例外シナリオの割合。 */
    private static double exceptionRateOf(double ratio) {
        RandomScenario random = RandomScenario.from(7L, ratio);
        int exceptions = 0;
        for (int i = 0; i < 200; i++) {
            if (raisesException(random.next().scenario())) {
                exceptions++;
            }
        }
        return exceptions / 200.0;
    }

    private static boolean raisesException(
            com.example.cargotracker.simulation.domain.model.valueobjects.Scenario scenario) {
        return scenario.exceptionType() != null
                || scenario.steps().stream().anyMatch(step ->
                        step == com.example.cargotracker.simulation.domain.model
                                .valueobjects.StepKind.REGISTER_EXCEPTION
                        || step == com.example.cargotracker.simulation.domain.model
                                .valueobjects.StepKind.RECORD_OFF_ROUTE_HANDLING
                        || step == com.example.cargotracker.simulation.domain.model
                                .valueobjects.StepKind.HOLD_CUSTOMS);
    }
}
