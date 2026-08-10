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
 * @param bookingId      予約 ID
 * @param trackingNumber 追跡番号
 * @param shipperName    荷主名
 * @param corporate      法人荷主か。<strong>割引が適用される相手である</strong>
 * @param origin         出発地
 * @param destination    目的地
 * @param cargoTypeLabel 貨物種別の表示名。<strong>列挙子名を利用者に見せない</strong>
 * @param weightKg       重量（kg）
 * @param hasException   例外が起きているか。<strong>料金調整の対象があることを示す</strong>
 * @param claimedOn      引取が済んだ日（IT13 レビュー C1）。
 *                       <strong>業務タイムゾーンの日付である</strong> — UTC で切ると、
 *                       日本時間の朝に済んだ引取が前日扱いになり月次の締めがずれる。
 *                       列が無かったころの引取は {@code null}
 */
public record PendingCargoView(
        String bookingId,
        String trackingNumber,
        String shipperName,
        boolean corporate,
        String origin,
        String destination,
        String cargoTypeLabel,
        BigDecimal weightKg,
        boolean hasException,
        java.time.LocalDate claimedOn) {
}
