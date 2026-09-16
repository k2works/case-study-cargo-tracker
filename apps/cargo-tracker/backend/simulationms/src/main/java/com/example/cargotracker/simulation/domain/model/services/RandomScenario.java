package com.example.cargotracker.simulation.domain.model.services;

import com.example.cargotracker.simulation.domain.model.valueobjects.CargoKind;
import com.example.cargotracker.simulation.domain.model.valueobjects.Scenario;
import com.example.cargotracker.simulation.domain.model.valueobjects.ScenarioInput;
import com.example.cargotracker.simulation.domain.model.valueobjects.StepKind;
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

    /**
     * 選べる貨物種別。<b>名簿を書き写さない</b>——{@link CargoKind} から数え上げる。
     *
     * <p>書き写すと、種別を足した人が付帯情報だけ足して抽選に入れ忘れる（あるいは
     * その逆で、<b>付帯情報の無い種別を引いて 422 で止まる</b>）。</p>
     */
    private static final List<String> CARGO_TYPES =
            java.util.Arrays.stream(CargoKind.values()).map(Enum::name).toList();

    /** 重量の範囲（kg）。業務が断らない幅に収める。 */
    private static final int MIN_WEIGHT_KG = 100;
    private static final int WEIGHT_SPREAD_KG = 4900;

    /** 到着期限までの日数。<b>短すぎると経路が組めない</b>ので下限を置く。 */
    private static final int MIN_DEADLINE_DAYS = 30;
    private static final int DEADLINE_SPREAD_DAYS = 90;

    /**
     * 例外を含むシナリオ。
     *
     * <p><b>名簿にしない。</b> シナリオが例外種別を宣言しているか、例外の工程を
     * 持っているかで決める——足した例外シナリオがここに載らないまま「例外が
     * 出ない」ことにならないように。</p>
     */
    private static boolean raisesException(Scenario scenario) {
        return scenario.exceptionType() != null
                || scenario.steps().stream().anyMatch(step ->
                        step == StepKind.REGISTER_EXCEPTION
                                || step == StepKind.RECORD_OFF_ROUTE_HANDLING
                                || step == StepKind.HOLD_CUSTOMS);
    }

    private final Random random;
    /**
     * 例外シナリオを選ぶ割合（US36 §受入基準 2）。
     *
     * <p><b>設定を受け取らないと、宣言だけの項目になる。</b> 検証して保存して
     * 画面に出しても、選び方に効いていなければ何も変わらない
     * ——定義済み未使用は配線漏れのサインである（IT17 のレビューで実測）。</p>
     */
    private final double exceptionRatio;

    private RandomScenario(long seed, double exceptionRatio) {
        // **java.util.Random を使う。** 仕様で並びが決まっているので、
        // JDK が変わっても同じ種から同じ並びが出る（SecureRandom は再現しない）。
        this.random = new Random(seed); // NOSONAR: 再現性が要る（暗号用途ではない）
        this.exceptionRatio = exceptionRatio;
    }

    /** 種から作る。<b>例外の割合は設定から受ける</b>（US36 §受入基準 2）。 */
    public static RandomScenario from(long seed, double exceptionRatio) {
        return new RandomScenario(seed, exceptionRatio);
    }

    /**
     * 種から作り、<b>すでに流した本数だけ進める</b>（US36 §受入基準 1・3）。
     *
     * <p>稼働は記憶を持たない（毎回 DB から組み直す）ので、位置を記憶に頼ると
     * <b>組み直すたびに先頭へ戻り、同じ条件を延々と流す</b>。本数から決めれば、
     * 同じ種の N 本目はいつ数え直しても同じ条件になる。</p>
     */
    public static RandomScenario from(long seed, double exceptionRatio, int alreadyDrawn) {
        RandomScenario random = new RandomScenario(seed, exceptionRatio);
        for (int i = 0; i < alreadyDrawn; i++) {
            random.next();
        }
        return random;
    }

    /** 次の条件を選ぶ。 */
    public ScenarioInput next() {
        // **例外の割合を先に決める。** どの群から引くかを決めてから中身を引く
        // ——一様に引いてから捨てると、引いた回数が結果で変わって再現できない。
        boolean wantsException = random.nextDouble() < exceptionRatio;
        List<Scenario> candidates = List.of(Scenario.values()).stream()
                .filter(scenario -> raisesException(scenario) == wantsException)
                .toList();
        Scenario scenario = pick(candidates.isEmpty()
                ? List.of(Scenario.values()) : candidates);
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
