package com.example.billingms.interfaces.rest;

import java.math.BigDecimal;
import java.util.List;

/**
 * 料金試算の依頼（US01-3）。
 *
 * <p><strong>係数は受け取らない。</strong>式は billingms が持つ（[ADR-028] 決定 6）
 * ——相手が係数を送れるようにすると、そこが 2 つ目の式になる。
 *
 * @param legs 区間ごとの両端の地域区分
 * @param weightKg 重量
 * @param cargoType 貨物種別
 */
public record QuoteRequest(List<QuoteLegRequest> legs, BigDecimal weightKg, String cargoType) {

    /** 試算の入力になる 1 区間。 */
    public record QuoteLegRequest(String loadRegion, String unloadRegion) {
    }
}
