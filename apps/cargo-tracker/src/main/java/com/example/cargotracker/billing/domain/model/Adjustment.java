package com.example.cargotracker.billing.domain.model;

/**
 * 料金調整（US21 の受入基準 6）。
 *
 * <p>例外（遅延・破損等）が発生している場合に、減額と補償費用を入力する。
 *
 * <p><strong>自動計算はしない。</strong> 減額の判断は業務であり、
 * 金額を機械が決めると根拠が説明できない。ここが持つのは
 * <strong>人が決めた額と、その理由</strong>である。
 *
 * @param reduction    減額。<strong>基本料金から引く</strong>
 * @param compensation 補償費用。<strong>基本料金に足す</strong>（代替輸送費など）
 * @param reason       調整の理由。<strong>必須である</strong> —
 *                     後から見て根拠が説明できない調整を残さない
 */
public record Adjustment(Money reduction, Money compensation, String reason) {

    public Adjustment {
        if (reduction == null || compensation == null) {
            throw new IllegalArgumentException("減額と補償費用は必須です（無ければ 0 円）");
        }
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("料金調整の理由は必須です");
        }
        reason = reason.strip();
    }

    /** 調整なし。 */
    public static Adjustment none() {
        return new Adjustment(Money.zeroYen(), Money.zeroYen(), "調整なし");
    }

    /** 金額が動くか。**「調整の記録があるが 0 円」も起こりうる。** */
    public boolean movesAmount() {
        return !reduction.isZero() || !compensation.isZero();
    }

    /** 基本料金に調整を適用する。<strong>減額してから補償を足す。</strong> */
    public Money applyTo(Money base) {
        return base.subtract(reduction).add(compensation);
    }
}
