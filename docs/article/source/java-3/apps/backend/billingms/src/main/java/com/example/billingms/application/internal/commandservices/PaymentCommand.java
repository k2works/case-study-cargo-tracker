package com.example.billingms.application.internal.commandservices;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 入金の確認（受入基準 23-3）。
 *
 * <p><strong>経理担当者が手で入れる。</strong>決済機関との連携先が無い（代替）。
 *
 * @param amountValue 入金額
 * @param paidAt 入金日
 * @param method 入金の方法
 * @param transactionReference 参照番号。無ければ {@code null}
 */
public record PaymentCommand(BigDecimal amountValue, LocalDate paidAt, String method,
        String transactionReference) {
}
