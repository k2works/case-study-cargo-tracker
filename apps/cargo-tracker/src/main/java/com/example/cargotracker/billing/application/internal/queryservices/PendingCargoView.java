package com.example.cargotracker.billing.application.internal.queryservices;

import java.math.BigDecimal;

/**
 * 請求対象の貨物 1 件（表示用。US21）。
 *
 * <p><strong>表示名への変換は Billing 側で行う</strong>（レビュー H11）。
 * {@code CargoTypeFactor} は Billing のドメインであり、
 * ACL ポートに載せると他 BC が Billing のドメインを参照することになる
 * （ArchUnit ルール 4 が実際に捕まえた）。ポートが運ぶのは素の値だけである。
 *
 * <p><strong>意味のまとまりごとに入れ子へ分けている</strong>（IT17 の R6）。
 * 以前は 10 個の要素が一列に並び、{@code origin} と {@code destination}、
 * {@code corporate} と {@code hasException} が同じ型で隣り合っていた。
 *
 * <p>画面が呼ぶ名前は委譲するアクセサで残している。
 *
 * @param bookingId      予約 ID
 * @param trackingNumber 追跡番号
 * @param shipper        荷主（誰への請求か・割引の可否）
 * @param cargo          貨物の仕様と経路
 * @param hasException   例外が起きているか。<strong>料金調整の対象があることを示す</strong>
 * @param claimedOn      引取が済んだ日（業務タイムゾーン。C1）
 */
public record PendingCargoView(
        String bookingId,
        String trackingNumber,
        Shipper shipper,
        CargoSpec cargo,
        boolean hasException,
        java.time.LocalDate claimedOn) {

    /**
     * 荷主。
     *
     * @param name      荷主名
     * @param corporate 法人荷主か。<strong>割引の可否を決める</strong>
     */
    public record Shipper(String name, boolean corporate) { }

    /**
     * 貨物の仕様と経路。
     *
     * @param origin      出発地
     * @param destination 目的地
     * @param typeLabel   貨物種別の表示名
     * @param weightKg    重量（kg）
     */
    public record CargoSpec(
            String origin, String destination, String typeLabel, BigDecimal weightKg) { }

    // --- 画面が呼ぶ名前（委譲するアクセサ）---

    /** @return 荷主名 */
    public String shipperName() {
        return shipper.name();
    }

    /** @return 法人荷主か */
    public boolean corporate() {
        return shipper.corporate();
    }

    /** @return 出発地 */
    public String origin() {
        return cargo.origin();
    }

    /** @return 目的地 */
    public String destination() {
        return cargo.destination();
    }

    /** @return 貨物種別の表示名 */
    public String cargoTypeLabel() {
        return cargo.typeLabel();
    }

    /** @return 重量（kg） */
    public BigDecimal weightKg() {
        return cargo.weightKg();
    }

}
