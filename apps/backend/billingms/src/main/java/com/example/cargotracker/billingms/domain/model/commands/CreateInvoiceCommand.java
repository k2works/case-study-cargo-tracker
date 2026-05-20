package com.example.cargotracker.billingms.domain.model.commands;

import org.axonframework.modelling.annotation.TargetEntityId;

/**
 * Invoice を新規作成するコマンド（US21）。
 *
 * @param invoiceId  請求 ID（UUID、呼び出し元が生成）
 * @param bookingId  対応する予約 ID
 * @param shipperId  荷主 ID
 */
public record CreateInvoiceCommand(
        @TargetEntityId String invoiceId,
        String bookingId,
        String shipperId) { }
