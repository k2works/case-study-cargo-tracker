package com.example.cargotracker.billingms.domain.model.commands;

import java.math.BigDecimal;
import org.axonframework.modelling.annotation.TargetEntityId;

/**
 * 入金確認・精算完了コマンド（US23）。
 *
 * @param invoiceId     請求 ID
 * @param paidAmount    入金額
 * @param paymentMethod 決済方法
 * @param operatorId    操作者 ID
 */
public record RecordPaymentCommand(
        @TargetEntityId String invoiceId,
        BigDecimal paidAmount,
        String paymentMethod,
        String operatorId) {
}
