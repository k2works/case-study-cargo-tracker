package com.example.cargotracker.booking.domain.model.valueobjects;

import com.example.cargotracker.shared.domain.error.BusinessRuleViolation;
import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 見積の概算額（US01）。
 *
 * <p><b>「請求額」ではない。</b> 見積は候補経路から数えた概算で、請求は実際に
 * 通った区間から数える。名前を分けておかないと、画面でも会話でも同じものとして
 * 扱われ、差が出たときに「どちらが正しいのか」という話になる——正しいのは
 * どちらでもなく、<b>入力が違う</b>だけである。</p>
 *
 * <p><b>共有カーネルには置かない</b>（{@code domain-model.md}「置かないもの」）。
 * billingms の {@code Money} とは別の型で、丸めも意味も違う。</p>
 *
 * <p><b>丸めはここ 1 か所だけ。</b> 各所で {@code setScale} を書くと、足す順で
 * 合計が変わる。計算の途中は丸めず、画面に出すときに {@link #roundToUnit()} で
 * 1 円単位にする。</p>
 *
 * @param amount 金額。<b>負にならない</b>
 * @param currency ISO 4217（この版は {@code JPY} だけを使う）
 */
public record EstimatedAmount(BigDecimal amount, String currency) {

    /** 計算の途中で持つ桁。<b>ここでは丸めない。</b> */
    private static final int SCALE = 4;

    public static final String JPY = "JPY";

    public EstimatedAmount {
        if (amount == null) {
            throw new BusinessRuleViolation("概算額がありません");
        }
        if (currency == null || currency.isBlank()) {
            throw new BusinessRuleViolation("通貨がありません");
        }
        if (amount.signum() < 0) {
            throw new BusinessRuleViolation("概算額が負になりました: " + amount + " " + currency);
        }
        amount = amount.setScale(SCALE, RoundingMode.HALF_UP);
    }

    public static EstimatedAmount yen(BigDecimal amount) {
        return new EstimatedAmount(amount, JPY);
    }

    public static EstimatedAmount zero() {
        return yen(BigDecimal.ZERO);
    }

    public EstimatedAmount multiply(BigDecimal factor) {
        return new EstimatedAmount(amount.multiply(factor), currency);
    }

    /** 画面と保存に載せるときの 1 円単位。 */
    public EstimatedAmount roundToUnit() {
        return new EstimatedAmount(amount.setScale(0, RoundingMode.HALF_UP), currency);
    }

    public boolean isZero() {
        return amount.signum() == 0;
    }
}
