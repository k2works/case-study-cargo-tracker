package com.example.cargotracker.simulation.domain.model.services;

import com.example.cargotracker.simulation.domain.model.valueobjects.Scenario;
import com.example.cargotracker.simulation.domain.model.valueobjects.ScenarioInput;
import java.math.BigDecimal;
import java.util.List;
import java.util.Random;

/**
 * 乱数でシナリオと条件を選ぶ（US36 §受入基準 1・3）。
 *
 * <p><b>種から同じ並びを作る。</b> 切り分けは「同じことをもう一度起こす」ことから
 * 始まる。再現できないと、たまたま出た失敗を追えない。</p>
 *
 * <p><b>再現するのは選んだ条件だけ</b>（注 N4）。実行の時刻と生成される識別子は
 * 種の外である。</p>
 *
 * <p><b>候補は名簿から回す。</b> 港や貨物種別を 1 つずつ書くと、足した値が
 * 選ばれないまま一覧にだけ並ぶ。</p>
 */
public final class RandomScenario {

    /**
     * 選べる港。
     *
     * <p><b>便が通っている港だけを並べる。</b> どの便も寄らない港は
     * {@link Scenario#NO_ROUTE} が専用に持っており、ここに混ぜると
     * 「成功するはずのシナリオがときどき失敗する」形になる——<b>失敗する条件は
     * 構造で決める</b>（IT16 の教訓）。</p>
     */
    private static final List<String> PORTS =
            List.of("JPTYO", "USNYC", "SGSIN", "NLRTM", "DEHAM", "CNSHA");

    /** 選べる貨物種別。<b>業務の列挙と同じ語</b>（境界で翻訳しない）。 */
    private static final List<String> CARGO_TYPES =
            List.of("GENERAL", "HAZARDOUS", "REFRIGERATED");

    /** 重量の範囲（kg）。業務が断らない幅に収める。 */
    private static final int MIN_WEIGHT_KG = 100;
    private static final int WEIGHT_SPREAD_KG = 4900;

    /** 到着期限までの日数。<b>短すぎると経路が組めない</b>ので下限を置く。 */
    private static final int MIN_DEADLINE_DAYS = 30;
    private static final int DEADLINE_SPREAD_DAYS = 90;

    private final Random random;

    private RandomScenario(long seed) {
        // **java.util.Random を使う。** 仕様で並びが決まっているので、
        // JDK が変わっても同じ種から同じ並びが出る（SecureRandom は再現しない）。
        this.random = new Random(seed); // NOSONAR: 再現性が要る（暗号用途ではない）
    }

    /** 種から作る。 */
    public static RandomScenario from(long seed) {
        return new RandomScenario(seed);
    }

    /** 次の条件を選ぶ。 */
    public ScenarioInput next() {
        Scenario scenario = pick(List.of(Scenario.values()));
        String origin = pick(PORTS);
        String destination = pickOtherThan(origin);
        return new ScenarioInput(scenario, origin, destination, pick(CARGO_TYPES),
                BigDecimal.valueOf(MIN_WEIGHT_KG + random.nextInt(WEIGHT_SPREAD_KG)),
                MIN_DEADLINE_DAYS + random.nextInt(DEADLINE_SPREAD_DAYS));
    }

    private <T> T pick(List<T> candidates) {
        return candidates.get(random.nextInt(candidates.size()));
    }

    /**
     * 出発地と違う港を選ぶ。
     *
     * <p><b>引き直さない。</b> 「同じなら引き直す」形にすると、引いた回数が
     * 港の並びで変わり、<b>そのあとの値がすべてずれる</b>——同じ種から同じ
     * 並びを作るという約束が、港の候補を 1 つ足すだけで壊れる。</p>
     */
    private String pickOtherThan(String origin) {
        List<String> others = PORTS.stream().filter(port -> !port.equals(origin)).toList();
        return others.get(random.nextInt(others.size()));
    }
}
