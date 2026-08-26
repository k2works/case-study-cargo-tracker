package com.example.billingms.interfaces.rest;

import com.example.billingms.application.internal.ChargeCalculation;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * 料金の算出結果（[ADR-027] 決定 3）。
 *
 * <p><strong>保存されていない値である。</strong>確定するまで精算書は存在しない。
 *
 * @param bookingId 予約番号
 * @param shipperName 荷主の社名
 * @param shipperType 荷主種別
 * @param basis 基本料金の根拠
 * @param baseAmount 基本料金
 * @param discountRate 割引率。<strong>割引が無ければ {@code null}</strong>（0% ではない）
 * @param discountAmount 割引額
 * @param misroute 誤配の記録（調整の根拠）
 * <p><strong>例外（遅延・破損）は載せない</strong>——本 IT では trackingms から引いて
 * いない（受入基準 21-6 の片肺）。載せる型だけ先に作ると「実装済みに見えて誰にも
 * 届かない」形になるため、US23 で引くときに足す。画面は「例外はこの画面には表示
 * されません」と言う。
 * @param cancellationFee キャンセル料
 * @param taxRate 税率
 * @param taxAmount 消費税
 * @param totalAmount 調整前の合計
 */
public record ChargeCalculationResponse(
        String bookingId,
        String shipperName,
        String shipperType,
        ChargeBasisResponse basis,
        MoneyResponse baseAmount,
        BigDecimal discountRate,
        MoneyResponse discountAmount,
        MisrouteResponse misroute,
        CancellationFeeResponse cancellationFee,
        BigDecimal taxRate,
        MoneyResponse taxAmount,
        MoneyResponse totalAmount) {

    /** 誤配の記録（21-6 の根拠）。**金額は決めない**——判断は経理担当者が行う。 */
    public record MisrouteResponse(Instant at, String locationUnLocode, String locationName) {
    }

    /** キャンセル料と、その算定根拠（US30-9）。 */
    public record CancellationFeeResponse(String bookingStatusAtCancel,
            String bookingStatusLabel, BigDecimal feeRate, MoneyResponse amount) {
    }

    private static final java.util.Map<String, String> STATUS_LABELS = java.util.Map.of(
            "PRELIMINARY", "仮受付",
            "ROUTE_PROPOSED", "経路提案中",
            "ROUTE_NOTIFIED", "荷主へ通知済",
            "CONFIRMED", "確定済",
            "TRACKING_ISSUED", "追跡番号発行済",
            "IN_TRANSIT", "輸送中");

    public static ChargeCalculationResponse from(ChargeCalculation calculation) {
        return new ChargeCalculationResponse(
                calculation.bookingId(),
                calculation.shipperName(),
                calculation.corporate() ? "CORPORATE" : "INDIVIDUAL",
                ChargeBasisResponse.from(calculation.charge()),
                MoneyResponse.from(calculation.baseAmount()),
                // **未設定は 0% ではない**（[ADR-012]）。null のまま返す
                calculation.discountRate() == null ? null : calculation.discountRate().value(),
                calculation.discountRate() == null ? null
                        : MoneyResponse.from(calculation.discountAmount()),
                calculation.misroute() == null ? null : new MisrouteResponse(
                        calculation.misroute().at(),
                        calculation.misroute().locationUnLocode(),
                        calculation.misroute().locationName()),
                calculation.cancellationFee() == null ? null : new CancellationFeeResponse(
                        calculation.cancellationFee().bookingStatusAtCancel().name(),
                        STATUS_LABELS.getOrDefault(
                                calculation.cancellationFee().bookingStatusAtCancel().name(),
                                calculation.cancellationFee().bookingStatusAtCancel().name()),
                        calculation.cancellationFee().feeRate(),
                        MoneyResponse.from(calculation.cancellationFee().amount())),
                calculation.taxRate().value(),
                MoneyResponse.from(calculation.taxAmount()),
                MoneyResponse.from(calculation.totalAmount()));
    }
}
