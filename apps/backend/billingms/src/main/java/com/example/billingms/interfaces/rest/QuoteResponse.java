package com.example.billingms.interfaces.rest;

/**
 * 料金試算の答え（US01-3）。
 *
 * <p><strong>基本料金だけを返す。</strong>割引も税も入れない——見積の時点では
 * 荷主が決まっていないことがあり、契約割引は請求の話である。
 *
 * @param baseAmount 基本料金
 */
public record QuoteResponse(MoneyResponse baseAmount) {
}
