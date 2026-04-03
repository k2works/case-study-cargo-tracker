package com.example.cargotracker.billing.domain.model.aggregates;

import com.example.cargotracker.billing.domain.model.valueobjects.ChargeStatus;

import java.math.BigDecimal;

/**
 * 輸送料金集約ルート。
 * 配送完了した予約に対して輸送料金を算出・管理する。
 */
public class FreightCharge {

    private final FreightId id;
    private final String bookingId;
    private ChargeStatus status;
    private final BigDecimal baseAmount;
    private BigDecimal adjustmentAmount;
    private BigDecimal totalAmount;

    private FreightCharge(FreightId id, String bookingId, ChargeStatus status,
                          BigDecimal baseAmount, BigDecimal adjustmentAmount, BigDecimal totalAmount) {
        if (id == null) throw new IllegalArgumentException("輸送料金 ID は null にできません");
        if (bookingId == null || bookingId.isBlank()) throw new IllegalArgumentException("予約 ID は null または空にできません");
        if (baseAmount == null) throw new IllegalArgumentException("基本料金は null にできません");
        if (baseAmount.compareTo(BigDecimal.ZERO) <= 0) throw new IllegalArgumentException("基本料金は正数でなければなりません");
        this.id = id;
        this.bookingId = bookingId;
        this.status = status;
        this.baseAmount = baseAmount;
        this.adjustmentAmount = adjustmentAmount;
        this.totalAmount = totalAmount;
    }

    /**
     * 輸送料金を算出して DRAFT 状態で作成する。
     * totalAmount = baseAmount（調整なし）
     */
    public static FreightCharge calculate(FreightId id, String bookingId, BigDecimal baseAmount) {
        return new FreightCharge(id, bookingId, ChargeStatus.DRAFT, baseAmount, BigDecimal.ZERO, baseAmount);
    }

    /**
     * ストアから輸送料金を再構成する。
     */
    public static FreightCharge reconstitute(FreightId id, String bookingId, ChargeStatus status,
                                             BigDecimal baseAmount, BigDecimal adjustmentAmount, BigDecimal totalAmount) {
        return new FreightCharge(id, bookingId, status, baseAmount, adjustmentAmount, totalAmount);
    }

    /**
     * 調整額を適用して totalAmount を更新する。DRAFT 状態のみ可能。
     */
    public void applyAdjustment(BigDecimal adjustmentAmount) {
        if (this.status == ChargeStatus.CONFIRMED) {
            throw new IllegalStateException("確定済みの輸送料金に調整額を適用できません");
        }
        this.adjustmentAmount = adjustmentAmount;
        this.totalAmount = this.baseAmount.add(adjustmentAmount);
    }

    /**
     * 輸送料金を確定する。DRAFT → CONFIRMED。
     */
    public void confirm() {
        if (this.status == ChargeStatus.CONFIRMED) {
            throw new IllegalStateException("すでに確定済みの輸送料金です");
        }
        this.status = ChargeStatus.CONFIRMED;
    }

    public FreightId getId() { return id; }
    public String getBookingId() { return bookingId; }
    public ChargeStatus getStatus() { return status; }
    public BigDecimal getBaseAmount() { return baseAmount; }
    public BigDecimal getAdjustmentAmount() { return adjustmentAmount; }
    public BigDecimal getTotalAmount() { return totalAmount; }
}
