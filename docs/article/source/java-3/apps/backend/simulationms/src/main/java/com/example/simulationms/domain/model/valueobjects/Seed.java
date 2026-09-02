package com.example.simulationms.domain.model.valueobjects;

import java.math.BigDecimal;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 乱数の種（US37-3・[ADR-031] 決定 1）。
 *
 * <p><strong>種から乱数器を作り直す。</strong>グローバルな乱数器を共有すると、
 * 並行実行の順序で取り出す値の並びが変わる——同じ種を指定しても再現できない。
 * 再現できないランダム実行は、落ちたときに報告する手段を持たない。
 *
 * <p>数値で持つのは、<strong>人が読んで写せる</strong>ようにするためである。
 * 落ちた実行を追うときは、画面から読み取って指定する。
 */
public record Seed(long value) {

    public static Seed of(long value) {
        return new Seed(value);
    }

    /**
     * 種を指定しなかったときの種。
     *
     * <p><strong>作った種は必ず記録する。</strong>記録しないと、指定しなかった実行だけが
     * 再現できない——実運用では指定しない方が普通である。
     */
    public static Seed random() {
        return new Seed(ThreadLocalRandom.current().nextLong());
    }

    /**
     * この種から乱数器を作る。
     *
     * <p>呼ぶたびに新しい乱数器を返す。<strong>同じ種からは、いつ・どのスレッドで
     * 呼んでも同じ並びが出る。</strong>
     *
     * @param exceptionRatio 例外シナリオを選ぶ割合（0〜1）
     */
    public ScenarioGenerator newGenerator(BigDecimal exceptionRatio) {
        return new ScenarioGenerator(this, exceptionRatio);
    }
}
