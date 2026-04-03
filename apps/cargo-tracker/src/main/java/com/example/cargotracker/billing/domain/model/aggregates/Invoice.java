package com.example.cargotracker.billing.domain.model.aggregates;

import com.example.cargotracker.billing.domain.model.valueobjects.PaymentStatus;

import java.math.BigDecimal;
import java.time.LocalDate;

public class Invoice {

    private final InvoiceId id;
    private final String bookingId;
    private final String freightChargeId;
    private final BigDecimal amount;
    private final LocalDate dueDate;
    private PaymentStatus paymentStatus;

    private Invoice(InvoiceId id, String bookingId, String freightChargeId,
                    BigDecimal amount, LocalDate dueDate, PaymentStatus paymentStatus) {
        if (id == null) throw new IllegalArgumentException("InvoiceId は null にできません");
        if (bookingId == null || bookingId.isBlank()) throw new IllegalArgumentException("予約 ID は必須です");
        if (freightChargeId == null || freightChargeId.isBlank()) throw new IllegalArgumentException("輸送料金 ID は必須です");
        if (amount == null) throw new IllegalArgumentException("金額は null にできません");
        if (amount.compareTo(BigDecimal.ZERO) <= 0) throw new IllegalArgumentException("金額は 0 より大きい必要があります");
        if (dueDate == null) throw new IllegalArgumentException("支払い期日は null にできません");
        this.id = id;
        this.bookingId = bookingId;
        this.freightChargeId = freightChargeId;
        this.amount = amount;
        this.dueDate = dueDate;
        this.paymentStatus = paymentStatus;
    }

    public static Invoice generate(InvoiceId id, String bookingId, String freightChargeId,
                                   BigDecimal amount, LocalDate dueDate) {
        return new Invoice(id, bookingId, freightChargeId, amount, dueDate, PaymentStatus.PENDING);
    }

    public static Invoice reconstitute(InvoiceId id, String bookingId, String freightChargeId,
                                       BigDecimal amount, LocalDate dueDate, PaymentStatus paymentStatus) {
        return new Invoice(id, bookingId, freightChargeId, amount, dueDate, paymentStatus);
    }

    public void confirmPayment() {
        if (this.paymentStatus != PaymentStatus.PENDING) {
            throw new IllegalStateException("支払い待ち状態の精算書のみ支払い確認できます");
        }
        this.paymentStatus = PaymentStatus.CONFIRMED;
    }

    public InvoiceId getId() { return id; }
    public String getBookingId() { return bookingId; }
    public String getFreightChargeId() { return freightChargeId; }
    public BigDecimal getAmount() { return amount; }
    public LocalDate getDueDate() { return dueDate; }
    public PaymentStatus getPaymentStatus() { return paymentStatus; }
}
