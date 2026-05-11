package com.example.billingms.domain.model.aggregates;

import com.example.billingms.domain.model.valueobjects.Money;
import com.example.billingms.domain.model.valueobjects.PaymentStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * 請求書集約ルート
 */
public class Invoice {

    private Long id;
    private final String invoiceNumber;
    private final String bookingId;
    private final String shipperId;
    private Money baseAmount;
    private Money finalAmount;
    private final BigDecimal taxRate;
    private PaymentStatus paymentStatus;
    private final LocalDate issuedAt;
    private final LocalDate dueDate;
    private final List<InvoiceLineItem> lineItems = new ArrayList<>();

    /** 新規作成コンストラクタ */
    public Invoice(String invoiceNumber, String bookingId, String shipperId,
                   LocalDate issuedAt, LocalDate dueDate) {
        Objects.requireNonNull(invoiceNumber, "invoiceNumber must not be null");
        Objects.requireNonNull(bookingId, "bookingId must not be null");
        this.invoiceNumber = invoiceNumber;
        this.bookingId = bookingId;
        this.shipperId = shipperId;
        this.baseAmount = Money.ofJpy(0);
        this.finalAmount = Money.ofJpy(0);
        this.taxRate = new BigDecimal("0.10");
        this.paymentStatus = PaymentStatus.PENDING;
        this.issuedAt = issuedAt;
        this.dueDate = dueDate;
    }

    /** 永続化済み再構成コンストラクタ */
    public Invoice(Long id, String invoiceNumber, String bookingId, String shipperId,
                   Money baseAmount, Money finalAmount, BigDecimal taxRate,
                   PaymentStatus paymentStatus, LocalDate issuedAt, LocalDate dueDate,
                   List<InvoiceLineItem> lineItems) {
        this(invoiceNumber, bookingId, shipperId, issuedAt, dueDate);
        this.id = id;
        this.baseAmount = baseAmount;
        this.finalAmount = finalAmount;
        if (lineItems != null) {
            this.lineItems.addAll(lineItems);
        }
        this.paymentStatus = paymentStatus;
    }

    /**
     * 明細を追加する
     */
    public void addLineItem(InvoiceLineItem item) {
        Objects.requireNonNull(item, "item must not be null");
        lineItems.add(item);
        this.baseAmount = lineItems.stream()
                .map(InvoiceLineItem::getAmount)
                .reduce(Money.ofJpy(0), Money::add);
    }

    /**
     * 最終金額を計算する（基本料金 + 消費税）
     */
    public Money calculateFinalAmount() {
        Money tax = baseAmount.multiply(taxRate);
        this.finalAmount = baseAmount.add(tax);
        return this.finalAmount;
    }

    /**
     * 料金を確定する
     */
    public void confirm() {
        if (this.paymentStatus != PaymentStatus.PENDING) {
            throw new IllegalStateException("確定済みの請求書は再確定できません");
        }
        calculateFinalAmount();
        this.paymentStatus = PaymentStatus.CONFIRMED;
    }

    public Long getId() { return id; }
    public String getInvoiceNumber() { return invoiceNumber; }
    public String getBookingId() { return bookingId; }
    public String getShipperId() { return shipperId; }
    public Money getBaseAmount() { return baseAmount; }
    public Money getFinalAmount() { return finalAmount; }
    public BigDecimal getTaxRate() { return taxRate; }
    public PaymentStatus getPaymentStatus() { return paymentStatus; }
    public LocalDate getIssuedAt() { return issuedAt; }
    public LocalDate getDueDate() { return dueDate; }
    public List<InvoiceLineItem> getLineItems() { return Collections.unmodifiableList(lineItems); }
}
