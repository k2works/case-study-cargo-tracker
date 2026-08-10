package com.example.cargotracker.billing.application.internal.outboundservices.acl;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/**
 * 請求の対象となる貨物を読む出力ポート（Billing → Booking の ACL。US21）。
 *
 * <p><strong>料金の算出に要るのは輸送実績である</strong>（受入基準 2）。
 * 経路・重量・貨物種別を Booking から受け取り、Billing の中で料金に換える。
 *
 * <p>運ぶのは<strong>素の値だけ</strong>である（ADR-005）。{@code Cargo} を渡すと
 * Billing が Booking のドメインを参照することになる（ArchUnit ルール 4）。
 */
public interface BillableCargoPort {

    /**
     * 請求書がまだ無い引取済みの貨物（請求対象一覧）。
     *
     * <p><strong>訂正・取り消しの申請中は含めない</strong>（IT12 持ち越し C8）。
     * 取り消されるかもしれない引取をもとに請求書を出すと、
     * 出した後で引取が無かったことになる。
     */
    List<BillableCargoSummary> findPending();

    /** 1 件（料金算出の画面で読む）。 */
    Optional<BillableCargoSummary> findByBookingId(String bookingId);

    /**
     * 請求に要る輸送実績（US21 の受入基準 2）。
     *
     * @param bookingId           予約 ID
     * @param trackingNumber      追跡番号。<strong>経理担当者が貨物を指す値である</strong>
     * @param shipperId           荷主 ID
     * @param shipperName         荷主名。<strong>誰への請求かが読めないと確認できない</strong>
     * @param corporate           法人荷主か。<strong>割引の可否を決める</strong>
     * @param origin              出発地
     * @param destination         目的地
     * @param cargoType           貨物種別（{@code GENERAL} / {@code HAZARDOUS} /
     *                            {@code REFRIGERATED}）。<strong>表示名への変換は
     *                            Billing 側で行う</strong>（{@code CargoTypeFactor} は
     *                            Billing のドメインであり、他 BC から参照させない）
     * @param weightKg            重量（kg）
     * @param distanceFactor      距離係数（区間数から求める）
     * @param claimed             引取が完了しているか
     * @param correctionRequested 訂正・取り消しが申請中か
     * @param hasException        例外（遅延・破損等）が起きているか。
     *                            <strong>料金調整の対象があることを示す</strong>
     */
    record BillableCargoSummary(
            String bookingId,
            String trackingNumber,
            String shipperId,
            String shipperName,
            boolean corporate,
            String origin,
            String destination,
            String cargoType,
            BigDecimal weightKg,
            BigDecimal distanceFactor,
            boolean claimed,
            boolean correctionRequested,
            boolean hasException) {
    }
}
