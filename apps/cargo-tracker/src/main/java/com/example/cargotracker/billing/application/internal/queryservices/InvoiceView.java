package com.example.cargotracker.billing.application.internal.queryservices;

import java.math.BigDecimal;

/**
 * 精算書 1 件の表示用（US21 / US22）。
 *
 * <p><strong>US22 の「割引計算の根拠」を画面に出すための形である。</strong>
 * 割引率・基本料金・割引後料金の 3 つがそろって初めて根拠になる。
 *
 * @param invoiceNumber    精算書番号
 * @param bookingId        予約 ID
 * @param trackingNumber   追跡番号。<strong>経理担当者が貨物を指す値である</strong>
 * @param shipperName      荷主名
 * @param baseAmount       基本料金（割引適用前）
 * @param discountPercent  適用した割引率（百分率）
 * @param discountAmount   割引額
 * @param adjustmentReason 料金調整の理由。<strong>調整が無ければ {@code null}</strong>
 * @param reduction        減額
 * @param compensation     補償費用
 * @param taxPercent       消費税率（百分率）
 * @param taxAmount        消費税額
 * @param totalAmount      請求総額
 * @param chargeStatusLabel 料金の状態の表示名
 * @param chargeStatusBadge 料金の状態のバッジ（正典は {@code ChargeStatus}）
 * @param confirmed        確定済みか
 */
public record InvoiceView(
        String invoiceNumber,
        String bookingId,
        String trackingNumber,
        String shipperName,
        BigDecimal baseAmount,
        BigDecimal discountPercent,
        BigDecimal discountAmount,
        String adjustmentReason,
        BigDecimal reduction,
        BigDecimal compensation,
        BigDecimal taxPercent,
        BigDecimal taxAmount,
        BigDecimal totalAmount,
        String chargeStatusLabel,
        String chargeStatusBadge,
        boolean confirmed) {

    /** 割引が適用されているか。**画面の出し分けは本述語をそのまま呼ぶ。** */
    public boolean hasDiscount() {
        return discountPercent != null && discountPercent.signum() > 0;
    }

    /** 料金調整があるか。 */
    public boolean hasAdjustment() {
        return adjustmentReason != null && !adjustmentReason.isBlank();
    }

    /** 割引後料金（基本料金 − 割引額）。**画面で引き算を書かない。** */
    public BigDecimal discountedAmount() {
        return baseAmount.subtract(discountAmount);
    }
}
