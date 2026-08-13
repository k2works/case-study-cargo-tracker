package com.example.cargotracker.routing.application.internal.queryservices;

import java.time.Instant;
import java.util.List;

/**
 * 航海の画面表示用データ（CQRS のクエリ側）。
 *
 * <p>寄港地・出発地・目的地は集約が {@code Schedule} から導く概念だが、
 * <strong>一覧のためだけに航海ごとの区間を読み直すと N+1 になる</strong>。
 * 読み取り側は SQL の集約関数で端点を求める。
 *
 * <p><strong>意味のまとまりごとに入れ子へ分けている</strong>（IT17 の R6）。
 * 以前は 11 個の要素が一列に並び、{@code origin} / {@code originName} /
 * {@code destination} / {@code destinationName} という
 * <strong>出発地と目的地の対が 4 つ続けて</strong>いた — 入れ替えても
 * コンパイルは通り、航路が逆に見えるだけである。
 *
 * <p>画面が呼ぶ名前は委譲するアクセサで残している。
 *
 * @param voyageNumber    航海番号
 * @param vesselName      船名
 * @param carrierName     運送会社
 * @param route           航路（出発地と目的地）
 * @param schedule        日程
 * @param callingPortCount 寄港地の数
 * @param cargoTypeLabels 取り扱える貨物種別の表示名
 */
public record VoyageView(
        String voyageNumber,
        String vesselName,
        String carrierName,
        Route route,
        Schedule schedule,
        int callingPortCount,
        List<String> cargoTypeLabels) {

    /**
     * 航路。
     *
     * @param origin          出発地 UN/LOCODE
     * @param originName      出発地の名称
     * @param destination     目的地 UN/LOCODE
     * @param destinationName 目的地の名称
     */
    public record Route(
            String origin, String originName, String destination, String destinationName) { }

    /**
     * 日程。
     *
     * @param departureTime 出発時刻
     * @param arrivalTime   到着時刻
     */
    public record Schedule(Instant departureTime, Instant arrivalTime) { }

    // --- 画面が呼ぶ名前（委譲するアクセサ）---

    /** @return 出発地 UN/LOCODE */
    public String origin() {
        return route.origin();
    }

    /** @return 出発地の名称 */
    public String originName() {
        return route.originName();
    }

    /** @return 目的地 UN/LOCODE */
    public String destination() {
        return route.destination();
    }

    /** @return 目的地の名称 */
    public String destinationName() {
        return route.destinationName();
    }

    /** @return 出発時刻 */
    public Instant departureTime() {
        return schedule.departureTime();
    }

    /** @return 到着時刻 */
    public Instant arrivalTime() {
        return schedule.arrivalTime();
    }


    public VoyageView {
        cargoTypeLabels = List.copyOf(cargoTypeLabels);
    }

    /** 直行便か。寄港地が無ければ直行である。 */
    public boolean isDirect() {
        return callingPortCount == 0;
    }
}
