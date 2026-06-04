package com.example.billingms.domain.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 荷役実績サマリ（domain-model.md L935-939）。
 *
 * <p>HandlingActivityAcl（Task 2.4）が handlingms から集計して返す荷役の総括情報。
 * 受領時刻・引取時刻はオプション（未発生時は null）、例外調整額（遅延補償・破損補償等の総計）
 * は 0 以上必須。FareCalculator では基本料金計算には使わず、例外時の補償額算定に使う
 * （Task 2.3 AdjustInvoiceCommand 等）。</p>
 *
 * @param receiveAt           受領時刻（RECEIVE 荷役活動の occurredAt、未受領なら null）
 * @param claimAt             引取時刻（CLAIM 荷役活動の occurredAt、未引取なら null）
 * @param exceptionAdjustment 例外発生時の補償額の総計（0 以上、null 不可）
 */
public record HandlingSummary(
        LocalDateTime receiveAt,
        LocalDateTime claimAt,
        BigDecimal exceptionAdjustment
) {

    public HandlingSummary {
        if (exceptionAdjustment == null || exceptionAdjustment.signum() < 0) {
            throw new IllegalArgumentException(
                    "exceptionAdjustment は 0 以上の値で必須です: " + exceptionAdjustment);
        }
        if (receiveAt != null && claimAt != null && claimAt.isBefore(receiveAt)) {
            throw new IllegalArgumentException(
                    "claimAt は receiveAt 以降である必要があります: receiveAt="
                            + receiveAt + ", claimAt=" + claimAt);
        }
    }
}
