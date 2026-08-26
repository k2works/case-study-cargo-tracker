package com.example.billingms.interfaces.rest;

import com.example.billingms.domain.model.Money;
import java.math.BigDecimal;

/**
 * 金額の応答。
 *
 * <p><strong>丸めたあとの値を返す</strong>（[ADR-027] 決定 2）。画面は
 * これをそのまま出すだけで、計算をしない——丸めが 2 か所に分かれると、
 * 画面と保存値が食い違う。
 *
 * @param value 金額（1 円単位）
 * @param currency 通貨コード
 */
public record MoneyResponse(BigDecimal value, String currency) {

    public static MoneyResponse from(Money money) {
        return money == null ? null : new MoneyResponse(money.amount(), money.currency());
    }
}
