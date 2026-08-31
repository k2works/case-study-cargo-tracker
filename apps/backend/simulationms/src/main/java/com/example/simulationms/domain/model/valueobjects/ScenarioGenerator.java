package com.example.simulationms.domain.model.valueobjects;

import java.math.BigDecimal;
import java.util.List;
import java.util.Random;

/**
 * 種から実行内容を作る（US37-1・[ADR-031] 決定 1）。
 *
 * <p><strong>この乱数器は共有しない。</strong>{@link Seed#newGenerator} が呼ばれるたびに
 * 新しく作られ、1 つの継続実行の中だけで使う。共有すると、並行して取り出したときに
 * 並びが変わり、記録した種で再現できなくなる。
 */
public final class ScenarioGenerator {

    /** 選べる港。**出発地と目的地は必ず違う**——同じだと予約が受け付けられない。 */
    private static final List<String> PORTS =
            List.of("JPTYO", "JPYOK", "USLAX", "USNYC", "SGSIN", "DEHAM", "NLRTM", "CNSHA");

    private static final List<String> CARGO_TYPES =
            List.of("GENERAL", "REFRIGERATED", "HAZARDOUS");

    private static final int MIN_WEIGHT_KG = 1;
    private static final int MAX_WEIGHT_KG = 30_000;

    /** 期限の幅。短すぎると経路候補が 0 件になり、シミュレーション自身の入力で落ちる。 */
    private static final int MIN_DEADLINE_DAYS = 30;
    private static final int MAX_DEADLINE_DAYS = 180;

    private final Random random;
    private final BigDecimal exceptionRatio;

    ScenarioGenerator(Seed seed, BigDecimal exceptionRatio) {
        if (exceptionRatio == null
                || exceptionRatio.compareTo(BigDecimal.ZERO) < 0
                || exceptionRatio.compareTo(BigDecimal.ONE) > 0) {
            throw new IllegalArgumentException(
                    "例外の割合は 0 から 1 の間で指定します: " + exceptionRatio);
        }
        this.random = new Random(seed.value());
        this.exceptionRatio = exceptionRatio;
    }

    /** 次の 1 件。 */
    public ScenarioRequest next() {
        Scenario scenario = pickScenario();
        String origin = pick(PORTS);
        String destination = pickOtherThan(origin);
        return new ScenarioRequest(scenario, origin, destination, pick(CARGO_TYPES),
                between(MIN_WEIGHT_KG, MAX_WEIGHT_KG),
                between(MIN_DEADLINE_DAYS, MAX_DEADLINE_DAYS));
    }

    /**
     * シナリオを選ぶ。
     *
     * <p><strong>比率を先に判定してから中身を選ぶ。</strong>全シナリオから一様に選ぶと、
     * 例外の割合が「例外シナリオの数 ÷ 全体」に固定され、設定した比率が効かない。
     */
    private Scenario pickScenario() {
        boolean raisesException = BigDecimal.valueOf(random.nextDouble())
                .compareTo(exceptionRatio) < 0;
        return raisesException ? pick(Scenario.exceptionScenarios()) : Scenario.standardTransport();
    }

    private String pickOtherThan(String origin) {
        String destination = pick(PORTS);
        while (destination.equals(origin)) {
            destination = pick(PORTS);
        }
        return destination;
    }

    private <T> T pick(List<T> candidates) {
        return candidates.get(random.nextInt(candidates.size()));
    }

    private int between(int min, int max) {
        return min + random.nextInt(max - min + 1);
    }
}
