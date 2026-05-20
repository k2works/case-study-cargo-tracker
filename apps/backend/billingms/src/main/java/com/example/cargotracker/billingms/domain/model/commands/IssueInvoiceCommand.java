package com.example.cargotracker.billingms.domain.model.commands;

import java.time.LocalDate;
import org.axonframework.modelling.annotation.TargetEntityId;

/**
 * 精算書発行コマンド（US23）。
 *
 * @param invoiceId   請求 ID
 * @param paymentDue  支払期限
 * @param operatorId  操作者 ID
 */
public record IssueInvoiceCommand(
        @TargetEntityId String invoiceId,
        LocalDate paymentDue,
        String operatorId) {
}
