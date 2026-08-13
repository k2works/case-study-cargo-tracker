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
 * <h2>なぜ写しを持つのか（**消そうとする人へ**）</h2>
 *
 * <p>予約詳細で現在地を示すために、<strong>荷役のテーブルを読みに行かない</strong>。
 * IT11 はこれを {@code handling_activity} を JOIN して読んでいたが、
 * <strong>BC をまたぐ SQL は ArchUnit にも JIG にも映らない</strong>（IT11 レビュー C28）。
 * 「Handling を直接読めば済む」と見えたときに<strong>止める理由はここにしか無い</strong> ——
 * 読みに行った瞬間、どの検査にも現れない結合が育ちはじめる。
 *
 * <p>荷役の登録は既に {@code HandlingActivityRegisteredEvent} で場所と日時を運んでいる。
 * <strong>運ばれてきた事実を写すのが結果整合の形である</strong>（ADR-009）。
 *
 * <p><strong>写しが無いことは正常である。</strong> 次の 2 つの場合に {@code null} になる。
 *
 * <ul>
 *   <li>誤配になっていない（大多数の予約）</li>
 *   <li><strong>列が無かったころに誤配になった</strong> —— 復元でこれを拒むと、
 *       その予約の画面ごと 500 になる</li>
 * </ul>
 *
 * <p><strong>「誤配になったか」と「写しがあるか」は別である</strong>（IT20 クローズ前レビュー H5）。
 * 初版は写しの有無だけを持ち、{@code isDetected()} が「写しがある」を返しながら
 * 名前は「誤配を検知したか」を名乗っていた —— <strong>写しの無い誤配を記録すると
 * 「誤配になっていない」と答える、嘘をつく述語</strong>だった。
 *
 * @param detected  誤配になったか
 * @param detection 検知した荷役の写し。<strong>誤配であっても無いことがある</strong>
 */
public record CargoMisroute(boolean detected, MisrouteDetection detection) {

    public CargoMisroute {
        if (!detected && detection != null) {
            throw new IllegalArgumentException(
                    "誤配になっていないのに検知の写しを持つことはできません");
        }
    }

    /** 誤配になっていない。 */
    public static CargoMisroute none() {
        return new CargoMisroute(false, null);
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
        return new CargoMisroute(true, detection);
    }

    /**
     * 永続化された値から載せ直す。
     *
     * <p><strong>写しの有無で誤配かどうかを決める。</strong> 復元が手にしているのは
     * 列の値だけであり、「列が無かったころに誤配になった貨物」と
     * 「そもそも誤配でない貨物」を区別する手立てが無い。
     * <strong>経路状態（{@code MISROUTED}）が正典であり、この写しは付録である。</strong>
     */
    public static CargoMisroute restored(MisrouteDetection detection) {
        return new CargoMisroute(detection != null, detection);
    }

    /** 検知した荷役の写しを持っているか。<strong>誤配かどうかとは別である。</strong> */
    public boolean hasDetection() {
        return detection != null;
    }
}
