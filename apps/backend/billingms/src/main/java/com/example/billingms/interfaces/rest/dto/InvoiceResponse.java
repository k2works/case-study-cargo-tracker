package com.example.billingms.interfaces.rest.dto;

import com.example.billingms.domain.projections.InvoiceLine;
import com.example.billingms.domain.projections.InvoiceSummary;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 請求書詳細レスポンス DTO（US21 / US23 / IT7 タスク 2.5）。
 *
 * <p>S23 請求詳細・算出画面で表示される請求書の全情報を返す。
 * {@code invoice} + {@code invoice_line} を結合した集約ビュー。</p>
 */
public record InvoiceResponse(
        String invoiceId,
        String bookingId,
        String shipperId,
        BigDecimal basicAmount,
        BigDecimal discountAmount,
        BigDecimal adjustmentAmount,
        BigDecimal totalAmount,
        String currency,
        String billingStatus,
        String invoiceNumber,
        LocalDate paymentDue,
        LocalDateTime paidAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        List<InvoiceLineResponse> lines
) {

    public static InvoiceResponse from(InvoiceSummary summary, List<InvoiceLine> lines) {
        return new InvoiceResponse(
                summary.getInvoiceId(),
                summary.getBookingId(),
                summary.getShipperId(),
                summary.getBasicAmount(),
                summary.getDiscountAmount(),
                summary.getAdjustmentAmount(),
                summary.getTotalAmount(),
                summary.getCurrency(),
                summary.getBillingStatus(),
                summary.getInvoiceNumber(),
                summary.getPaymentDue(),
                summary.getPaidAt(),
                summary.getCreatedAt(),
                summary.getUpdatedAt(),
                lines.stream().map(InvoiceLineResponse::from).toList()
        );
    }
}
