package com.example.billingms.domain.model;

import java.math.BigDecimal;

/**
 * キャンセル料（US30-9・正典のビジネスルール 6）。
 *
 * <p><strong>IT6 から 3 イテレーション繰り越されてきた。</strong>IT6 は「US30 で一括して
 * 入れる」と書き、IT9 は算定する場所が無いとして画面に「算定していません」と書いた。
 *
 * <p><strong>算定根拠（状態・料率）を保持する。</strong>金額だけ残すと、荷主から
 * 問われたときになぜその額かを言えない。
 *
 * @param bookingStatusAtCancel キャンセルを申請した時点の予約の状態
 * @param feeRate 適用した料率
 * @param amount キャンセル料
 */
public record CancellationFee(CancelledAtStatus bookingStatusAtCancel, BigDecimal feeRate,
        Money amount) {

    /**
     * 申請時の状態から算定する。
     *
     * <p><strong>丸めは {@link Money} が行う</strong>（[ADR-027] 決定 2）。
     */
    public static CancellationFee forStatus(CancelledAtStatus status, Money baseAmount) {
        if (status == null) {
            throw new IllegalArgumentException("キャンセル時の状態を指定してください");
        }
        if (baseAmount == null) {
            throw new IllegalArgumentException("基本料金を指定してください");
        }
        return new CancellationFee(status, status.feeRate(),
                baseAmount.multiply(status.feeRate()));
    }

    /** 料金が発生するか。**0 円のキャンセル料は「無い」ではなく「0 円」である。** */
    public boolean applies() {
        return feeRate.signum() > 0;
    }
}
