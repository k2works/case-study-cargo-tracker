package com.example.cargotracker.billingms.interfaces.rest.dto;

import com.example.cargotracker.billingms.infrastructure.persistence.InvoiceRecord;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 請求レスポンス DTO（US21）。
 */
public record InvoiceResponse(
        String invoiceId,
        String bookingId,
        String shipperId,
        BigDecimal basicAmount,
        BigDecimal discountAmount,
        BigDecimal totalAmount,
        String currency,
        String billingStatus,
        String invoiceNumber,
        LocalDate paymentDue,
        LocalDateTime paidAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {

    public static InvoiceResponse from(InvoiceRecord record) {
        return new InvoiceResponse(
                record.getInvoiceId(),
                record.getBookingId(),
                record.getShipperId(),
                record.getBasicAmount(),
                record.getDiscountAmount(),
                record.getTotalAmount(),
                record.getCurrency(),
                record.getBillingStatus(),
                record.getInvoiceNumber(),
                record.getPaymentDue(),
                record.getPaidAt(),
                record.getCreatedAt(),
                record.getUpdatedAt());
    }
}
