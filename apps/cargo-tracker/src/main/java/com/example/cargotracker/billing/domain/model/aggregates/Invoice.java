package com.example.cargotracker.billing.domain.model.aggregates;

import com.example.cargotracker.billing.domain.model.valueobjects.DiscountPolicy;
import com.example.cargotracker.billing.domain.model.valueobjects.PaymentStatus;

import java.time.LocalDate;

public class Invoice {

    private final InvoiceId invoiceId;
    private final String bookingId;
    private final int totalAmountValue;
    private final DiscountPolicy discountPolicy;
    private final int discountedAmountValue;
    private PaymentStatus paymentStatus;
    private final LocalDate dueDate;

    public Invoice(
            InvoiceId invoiceId,
            String bookingId,
            int totalAmountValue,
            DiscountPolicy discountPolicy,
            LocalDate dueDate
    ) {
        if (invoiceId == null) throw new IllegalArgumentException("invoiceId must not be null");
        if (bookingId == null || bookingId.isBlank()) throw new IllegalArgumentException("bookingId must not be blank");
        if (totalAmountValue < 0) throw new IllegalArgumentException("totalAmountValue must not be negative");
        if (discountPolicy == null) throw new IllegalArgumentException("discountPolicy must not be null");
        if (dueDate == null) throw new IllegalArgumentException("dueDate must not be null");

        this.invoiceId = invoiceId;
        this.bookingId = bookingId;
        this.totalAmountValue = totalAmountValue;
        this.discountPolicy = discountPolicy;
        this.discountedAmountValue = discountPolicy.apply(totalAmountValue);
        this.paymentStatus = PaymentStatus.PENDING;
        this.dueDate = dueDate;
    }

    public static Invoice reconstruct(
            InvoiceId invoiceId,
            String bookingId,
            int totalAmountValue,
            DiscountPolicy discountPolicy,
            int discountedAmountValue,
            PaymentStatus paymentStatus,
            LocalDate dueDate
    ) {
        Invoice invoice = new Invoice(invoiceId, bookingId, totalAmountValue, discountPolicy, dueDate);
        invoice.paymentStatus = paymentStatus;
        return invoice;
    }

    public void confirmPayment() {
        if (paymentStatus != PaymentStatus.PENDING) {
            throw new IllegalStateException("精算済みまたは期限超過の請求書は確認できません。");
        }
        this.paymentStatus = PaymentStatus.CONFIRMED;
    }

    public InvoiceId getInvoiceId() {
        return invoiceId;
    }

    public String getBookingId() {
        return bookingId;
    }

    public int getTotalAmountValue() {
        return totalAmountValue;
    }

    public DiscountPolicy getDiscountPolicy() {
        return discountPolicy;
    }

    public int getDiscountedAmountValue() {
        return discountedAmountValue;
    }

    public PaymentStatus getPaymentStatus() {
        return paymentStatus;
    }

    public LocalDate getDueDate() {
        return dueDate;
    }
}
