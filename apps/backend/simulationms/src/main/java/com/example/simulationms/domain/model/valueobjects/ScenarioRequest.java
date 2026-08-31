package com.example.simulationms.domain.model.valueobjects;

/**
 * 乱数が選んだ 1 件の実行内容（US37-1）。
 *
 * <p>出発地・目的地・貨物種別・重量・期限を持つ。<strong>これらは業務として
 * 成り立つ組み合わせでなければならない</strong>——出発地と目的地が同じ予約は
 * そもそも受け付けられず、シミュレーションが自分の入力で落ちることになる。
 *
 * @param scenario 実行するシナリオ
 * @param origin 出発地の UN/LOCODE
 * @param destination 目的地の UN/LOCODE
 * @param cargoType 貨物種別
 * @param weightKg 重量（kg）
 * @param deadlineDays 今日から到着期限までの日数
 */
public record ScenarioRequest(Scenario scenario, String origin, String destination,
        String cargoType, int weightKg, int deadlineDays) {

    public ScenarioRequest {
        if (origin.equals(destination)) {
            throw new IllegalArgumentException(
                    "出発地と目的地が同じです: " + origin);
        }
    }

    /**
     * 再現の突き合わせに使う形。
     *
     * <p>並びが同じかを比べるのは検査であり、業務の表示ではない。
     */
    @Override
    public String toString() {
        return "%s %s→%s %s %dkg %dd".formatted(
                scenario.id(), origin, destination, cargoType, weightKg, deadlineDays);
    }
}
