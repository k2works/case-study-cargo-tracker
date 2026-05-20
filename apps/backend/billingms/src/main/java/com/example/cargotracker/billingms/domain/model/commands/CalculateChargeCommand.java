package com.example.cargotracker.billingms.domain.model.commands;

import java.math.BigDecimal;
import org.axonframework.modelling.annotation.TargetEntityId;

/**
 * 輸送料金を算出・確定するコマンド（US21）。
 *
 * @param invoiceId      請求 ID
 * @param baseAmount     基本料金（算出システムが計算）
 * @param currencyCode   通貨コード（例: "JPY"）
 * @param discountRate   割引率（0.0〜0.30、法人割引適用時）
 * @param operatorId     操作者 ID
 */
public record CalculateChargeCommand(
        @TargetEntityId String invoiceId,
        BigDecimal baseAmount,
        String currencyCode,
        BigDecimal discountRate,
        String operatorId) { }
