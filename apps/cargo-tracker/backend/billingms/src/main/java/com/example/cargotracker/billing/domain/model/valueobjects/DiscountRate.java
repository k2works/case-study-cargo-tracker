package com.example.cargotracker.billing.domain.model.valueobjects;

import com.example.cargotracker.shared.domain.error.BusinessRuleViolation;
import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 契約割引率（US22 §受入基準 2）。
 *
 * <p><b>範囲（0〜30%）は型が守る。</b> 呼ぶ側の検査に任せると、呼ぶ場所が増えた
 * ぶんだけ抜ける。</p>
 *
 * <p><b>bookingms にも同名の型があるが、これは billingms の型である。</b>
 * 共有カーネルには置かない——同じ名前でも BC ごとに意味と使い道が違う
 * （bookingms は契約の登録、billingms は請求への適用）。</p>
 */
public record DiscountRate(BigDecimal value) {

    private static final BigDecimal MAX = new BigDecimal("0.3000");
    private static final int SCALE = 4;

    public DiscountRate {
        if (value == null) {
            throw new BusinessRuleViolation("割引率がありません");
        }
        if (value.signum() < 0) {
            throw new BusinessRuleViolation("割引率が負です: " + value);
        }
        if (value.compareTo(MAX) > 0) {
            throw new BusinessRuleViolation("割引率は 30% までです: " + value);
        }
        value = value.setScale(SCALE, RoundingMode.HALF_UP);
    }

    public static DiscountRate of(BigDecimal value) {
        return new DiscountRate(value);
    }

    /**
     * 割引率が無い（個人荷主・契約の無い法人）ときは 0%。
     *
     * <p><b>落とさない。</b> スナップショットの割引率は NULL でありうる。</p>
     */
    public static DiscountRate ofNullable(BigDecimal value) {
        return value == null ? none() : of(value);
    }

    public static DiscountRate none() {
        return new DiscountRate(BigDecimal.ZERO);
    }

    /** 画面と明細に出す百分率（15% なら 15）。 */
    public BigDecimal percentage() {
        return value.multiply(new BigDecimal("100")).stripTrailingZeros();
    }

    public boolean isZero() {
        return value.signum() == 0;
    }

    /** 基本料金に当てたときの<b>割引額</b>。丸めは {@link Money} の中で行う。 */
    public Money appliedTo(Money base) {
        return base.multiply(value);
    }
}
