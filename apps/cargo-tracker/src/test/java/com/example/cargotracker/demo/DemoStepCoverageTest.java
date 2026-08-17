package com.example.cargotracker.demo;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.cargotracker.support.PostgreSQLIntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * すべての手順に実行内容が登録されていることを確かめる。
 *
 * <p><strong>対応表の埋め忘れは、静かな欠陥である。</strong> 手順を足して登録を
 * 忘れると、その手順では<strong>何も起きないまま次へ進む</strong> —— 業務としては
 * 荷役を飛ばして引き取ったことになるが、画面には「進んだ」としか出ない。
 *
 * <p><strong>名簿方式の検査は、載っていないものを通してはならない。</strong>
 * 載せ忘れたものほど漏れる（ADR-015 で 3 回素通りさせた）。ここでは
 * <strong>列挙のすべての値</strong>を突き合わせる。
 */
class DemoStepCoverageTest extends PostgreSQLIntegrationTestBase {

    @Autowired
    private DemoStepExecutor executor;

    @Test
    void すべての手順に実行内容が登録されている() {
        assertThat(executor.registeredSteps())
                .as("**列挙のすべての値**に実行内容がある（載っていないものを通さない）")
                .containsExactlyInAnyOrder(DemoStep.values());
    }

    /**
     * <strong>手順の総数と画面の分母は同じものを見る。</strong> 別々に持つと、
     * 手順を足したときに片方だけが取り残される。
     */
    @Test
    void 手順の数は列挙から導く() {
        assertThat(DemoStep.count())
                .as("列挙の値の数がそのまま手順の数である")
                .isEqualTo(DemoStep.values().length)
                .isPositive();
        assertThat(DemoStep.values()[DemoStep.count() - 1].isLast())
                .as("最後の手順が最後だと分かる")
                .isTrue();
    }

    /**
     * <strong>貨物は最初の手順から始まり、最後の手順で終わる。</strong>
     * 途中から始まると、便も荷主も無いまま予約しようとする。
     */
    @Test
    void 貨物は最初の手順から始まる() {
        DemoCargoRun cargo = new DemoCargoRun(
                DemoScenario.random(java.util.random.RandomGenerator.getDefault(), 1));

        assertThat(cargo.nextStep()).isEqualTo(DemoStep.values()[0]);
        assertThat(cargo.finished()).isFalse();

        for (int i = 0; i < DemoStep.count(); i++) {
            cargo.advance();
        }
        assertThat(cargo.finished())
                .as("最後の手順を終えたら完了になる")
                .isTrue();
    }
}
