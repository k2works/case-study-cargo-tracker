package com.example.billingms.interfaces.rest;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 入金の確認（受入基準 23-3）。
 *
 * <p><strong>経理担当者が手で入れる</strong>——決済機関との連携先が無い（代替）。
 *
 * @param amountValue 入金額
 * @param paidAt 入金日。<strong>日付である</strong>——通帳に時刻は無い
 * @param method 入金の方法
 * @param transactionReference 参照番号（振込の照会番号など）
 */
public record ConfirmPaymentRequest(BigDecimal amountValue, LocalDate paidAt, String method,
        String transactionReference) {
}
