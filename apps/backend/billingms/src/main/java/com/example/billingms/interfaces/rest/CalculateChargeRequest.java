package com.example.billingms.interfaces.rest;

import java.math.BigDecimal;
import java.util.List;

/**
 * 料金の確定要求（US21-4）。
 *
 * <p><strong>調整はここでまとめて送る</strong>（[ADR-027] 決定 3）。算出中は保存しない
 * ため、画面が積んだ明細を確定の瞬間に受け取る。
 *
 * @param adjustments 料金調整の明細
 */
public record CalculateChargeRequest(List<Adjustment> adjustments) {

    /**
     * 調整 1 行。
     *
     * @param description 調整の内容（根拠）。<strong>空は断る</strong>（決定 6）
     * @param amountValue 金額。減額は負、補償費用は正
     */
    public record Adjustment(String description, BigDecimal amountValue) {
    }
}
