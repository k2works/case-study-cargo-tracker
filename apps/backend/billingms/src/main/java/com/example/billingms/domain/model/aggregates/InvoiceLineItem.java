package com.example.billingms.domain.model.aggregates;

import com.example.billingms.domain.model.valueobjects.Money;

import java.util.Objects;

/**
 * 請求明細エンティティ
 */
public class InvoiceLineItem {

    private Long id;
    private final String description;
    private final Money amount;
    private final int seqNumber;

    public InvoiceLineItem(String description, Money amount, int seqNumber) {
        Objects.requireNonNull(description, "description must not be null");
        Objects.requireNonNull(amount, "amount must not be null");
        this.description = description;
        this.amount = amount;
        this.seqNumber = seqNumber;
    }

    public InvoiceLineItem(Long id, String description, Money amount, int seqNumber) {
        this(description, amount, seqNumber);
        this.id = id;
    }

    public Long getId() { return id; }
    public String getDescription() { return description; }
    public Money getAmount() { return amount; }
    public int getSeqNumber() { return seqNumber; }
}
