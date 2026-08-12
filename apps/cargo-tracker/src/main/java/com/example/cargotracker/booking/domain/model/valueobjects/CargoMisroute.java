package com.example.cargotracker.booking.domain.model.valueobjects;

/**
 * 予約が持つ「誤配を検知した」という記録（US28 / C28。IT20 の D1 で {@code Cargo} から切り出した）。
 *
 * <p><strong>なぜ集約から出したか。</strong> {@code Cargo} が持つ他の可変フィールド
 * （{@code progress} / {@code claim}）は<strong>予約そのものの状態</strong>であり、予約の
 * 状態遷移が動かす。これだけは違う —— <strong>他の BC（Handling）で起きた事実の写しであり、
 * 予約の状態遷移とは無関係に届く</strong>（ADR-009 の結果整合）。写しであることの規則
 * （なぜ持つのか・無いことがありうるのはなぜか）を集約本体に書くと、
 * <strong>予約の状態機械を読みたい人が、他 BC の事情を読まされる</strong>。
 *
 * <p><strong>誤配は予約状態を変えない。</strong> 動くのは経路状態（{@link CargoRouting}）だけで
 * あり、貨物は輸送中のままである。したがってこの型は状態遷移について何も知らない。
 *
 * <p><strong>写しが無いことは正常である。</strong> 次の 2 つの場合に {@code null} になる。
 *
 * <ul>
 *   <li>誤配になっていない（大多数の予約）</li>
 *   <li><strong>列が無かったころに誤配になった</strong> —— 復元でこれを拒むと、
 *       その予約の画面ごと 500 になる</li>
 * </ul>
 *
 * @param detection 検知した荷役の写し。誤配でなければ {@code null}
 */
public record CargoMisroute(MisrouteDetection detection) {

    /** 誤配になっていない。 */
    public static CargoMisroute none() {
        return new CargoMisroute(null);
    }

    /**
     * 誤配を検知した。
     *
     * <p><strong>写しが無くても拒まない。</strong> 荷役のイベントは場所か日時のどちらかを
     * 欠くことがあり、{@link MisrouteDetection#reconstruct} はそのとき {@code null} を返す。
     * ここで拒むと<strong>誤配の反映そのものが例外で止まり</strong>、経路状態が
     * {@code MISROUTED} にならないまま輸送が続く。
     *
     * <p><strong>写しは「予約詳細で現在地を示すための付録」であり、
     * 誤配という事実の成立条件ではない。</strong>
     */
    public static CargoMisroute detected(MisrouteDetection detection) {
        return new CargoMisroute(detection);
    }

    /** 永続化された値から載せ直す（無いことがありうる。上記の 2 つ目の場合）。 */
    public static CargoMisroute restored(MisrouteDetection detection) {
        return new CargoMisroute(detection);
    }

    /** 誤配を検知しているか。 */
    public boolean isDetected() {
        return detection != null;
    }
}
